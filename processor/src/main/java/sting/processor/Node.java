package sting.processor;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.stream.JsonGenerator;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.realityforge.proton.ElementsUtil;

final class Node
{
  /**
   * The component graph that created this node.
   */
  @Nonnull
  private final ComponentGraph _componentGraph;
  /**
   * The binding for the node.
   * May be null if it represents an Injector.
   */
  @Nullable
  private final Binding _binding;
  /**
   * The edges to nodes that this node depends upon.
   */
  @Nonnull
  private final Map<ServiceRequest, Edge> _dependsOn = new LinkedHashMap<>();
  /**
   * The edges to nodes that use this node.
   */
  @Nonnull
  private final Set<Edge> _usedBy = new HashSet<>();
  /**
   * True if the node is explicitly from an eager binding or implicitly eager by being
   * a (transitive) dependency of an eager binding.
   */
  private boolean _eager;
  /**
   * The shortest path from a top level dependency to this node.
   */
  private int _depth = Integer.MAX_VALUE;
  /**
   * Name within the context of the Injector. The name MUST be unique and will be generated by the processor.
   */
  @Nullable
  private String _name;
  /**
   * The java type of the value provided by the node.
   * this will be null if _binding is null.
   */
  @Nullable
  private final TypeMirror _type;
  /**
   * Is the visibility of the type effectively public. This means that the type and
   * all enclosing types must have a public modifier.
   */
  private final boolean _public;
  /**
   * The Fragment node that this node is derived from if it is from a Fragment provider method.
   */
  @Nullable
  private FragmentNode _fragment;

  /**
   * Constructor used to construct a Node for the Injector.
   *
   * @param componentGraph the object graph
   */
  Node( @Nonnull final ComponentGraph componentGraph )
  {
    this( componentGraph,
          null,
          componentGraph.getInjector().getOutputs().toArray( new ServiceRequest[ 0 ] ) );
  }

  /**
   * Constructor used to construct a Node for a binding.
   *
   * @param binding the binding.
   */
  Node( final ComponentGraph componentGraph, @Nonnull final Binding binding )
  {
    this( componentGraph, binding, binding.getDependencies() );
  }

  private Node( @Nonnull final ComponentGraph componentGraph,
                @Nullable final Binding binding,
                @Nonnull final ServiceRequest[] dependencies )
  {
    _componentGraph = Objects.requireNonNull( componentGraph );
    _binding = binding;
    for ( final ServiceRequest dependency : dependencies )
    {
      _dependsOn.put( dependency, new Edge( this, dependency ) );
    }
    if ( null != _binding )
    {
      final Element element = binding.getElement();
      final Binding.Kind kind = binding.getKind();
      _type =
        Binding.Kind.INPUT == kind ? binding.getPublishedServices().get( 0 ).getCoordinate().getType() :
        Binding.Kind.INJECTABLE == kind ? element.getEnclosingElement().asType() :
        ( (ExecutableElement) element ).getReturnType();
      _public = TypeKind.DECLARED != _type.getKind() ||
                ElementsUtil.isEffectivelyPublic( (TypeElement) ( (DeclaredType) _type ).asElement() );
    }
    else
    {
      _type = null;
      _public = true;
    }
  }

  boolean isFromProvides()
  {
    return null != _binding && Binding.Kind.PROVIDES == _binding.getKind();
  }

  @Nonnull
  String getName()
  {
    assert null != _name;
    return _name;
  }

  void setName( @Nonnull final String name )
  {
    _name = Objects.requireNonNull( name );
  }

  @Nonnull
  TypeMirror getType()
  {
    assert null != _type;
    return _type;
  }

  boolean isPublic()
  {
    return _public;
  }

  boolean isEager()
  {
    return _eager;
  }

  void markNodeAndUpstreamAsEager()
  {
    if ( !_eager )
    {
      _eager = true;
      // Propagate eager flag to all nodes that this node uses unless
      // the service is a Supplier style service. Those can be non-eager
      // as they do not need to be created until they are accessed
      for ( final Edge edge : _dependsOn.values() )
      {
        if ( !edge.getServiceRequest().getKind().isSupplier() )
        {
          edge.getSatisfiedBy().forEach( Node::markNodeAndUpstreamAsEager );
        }
      }
    }
  }

  boolean hasNoBinding()
  {
    return null == _binding;
  }

  @Nonnull
  Binding getBinding()
  {
    assert null != _binding;
    return _binding;
  }

  @Nonnull
  Collection<Edge> getDependsOn()
  {
    return _dependsOn.values();
  }

  @Nonnull
  Set<Edge> getUsedBy()
  {
    return _usedBy;
  }

  boolean isDepthNotSet()
  {
    return Integer.MAX_VALUE == _depth;
  }

  int getDepth()
  {
    return _depth;
  }

  void setDepth( final int depth )
  {
    _depth = depth;
  }

  void usedBy( @Nonnull final Edge edge )
  {
    assert !_usedBy.contains( edge );
    _usedBy.add( edge );
  }

  void setFragment( @Nonnull final FragmentNode fragment )
  {
    assert isFromProvides();
    assert null == _fragment;
    _fragment = fragment;
  }

  @Nonnull
  FragmentNode getFragment()
  {
    assert null != _fragment;
    return _fragment;
  }

  /**
   * Describe a node as a single line.
   * Typically this is used when emitting messages to user.
   *
   * @param connector a 3-character string that appears between type label and the node description. Typically
   *                  used for describing connectors between successive nodes.
   * @return the string description.
   */
  @Nonnull
  String describe( @Nonnull final String connector )
  {
    return "  " + getTypeLabel() + connector + " " + describeBinding();
  }

  @Nonnull
  String getTypeLabel()
  {
    return null == _binding ? "[Injector]   " : _binding.getTypeLabel();
  }

  @Nonnull
  String describeBinding()
  {
    return null == _binding ?
           _componentGraph.getInjector().getElement().getQualifiedName().toString() :
           _binding.describe();
  }

  void write( @Nonnull final JsonGenerator g )
  {
    g.writeStartObject();
    assert null != _binding;
    g.write( "id", _binding.getId() );
    final Binding.Kind kind = _binding.getKind();
    g.write( "kind", kind.name() );
    if ( _eager )
    {
      g.write( "eager", true );
    }
    if ( !_dependsOn.isEmpty() )
    {
      g.writeStartArray( "dependencies" );
      for ( final Edge edge : _dependsOn.values() )
      {
        g.writeStartObject();
        edge.getServiceRequest().getService().getCoordinate().write( g );
        g.writeStartArray( "supportedBy" );
        for ( final Node node : edge.getSatisfiedBy() )
        {
          g.write( node.getBinding().getId() );
        }
        g.writeEnd();
        g.writeEnd();
      }
      g.writeEnd();
    }
    g.writeEnd();
  }
}
