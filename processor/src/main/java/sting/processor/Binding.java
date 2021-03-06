package sting.processor;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.json.stream.JsonGenerator;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

final class Binding
{
  /**
   * The kind of the binding.
   */
  @Nonnull
  private final Kind _kind;
  /**
   * A unique identifier for the binding which can be specified by the developer or derived automatically.
   * For an INJECTABLE binding this is the fully qualified name of the class. For other bindings it is
   * the fully qualified name of the class combined with the name of the method.
   */
  @Nonnull
  private final String _id;
  /**
   * Is the binding eager or lazy. Eager bindings are instantiated after the injector is instantiated
   * and before it is made accessible to user-code.
   */
  private final boolean _eager;
  /**
   * The element that created this binding.
   * This will be one of;
   * <ul>
   *   <lI>A {@link javax.lang.model.element.TypeElement} of the injector for {@link Kind#INPUT} binding</lI>
   *   <lI>A {@link javax.lang.model.element.ExecutableElement} of a constructor for {@link Kind#INJECTABLE} binding</lI>
   *   <lI>A {@link javax.lang.model.element.ExecutableElement} of a method for {@link Kind#PROVIDES} binding</lI>
   * </ul>
   */
  @Nonnull
  private final Element _element;
  /**
   * The dependencies that need to be supplied when creating a binding instance.
   */
  @Nonnull
  private final ServiceRequest[] _dependencies;
  /**
   * The service specifications published by this binding.
   */
  @Nonnull
  private final List<ServiceSpec> _publishedServices;
  /**
   * Flag indicating whether this binding will always create a component or may produce a null value.
   */
  private final boolean _optional;
  /**
   * The descriptor that created the binding.
   */
  private Object _owner;

  Binding( @Nonnull final Kind kind,
           @Nonnull final String id,
           @Nonnull final List<ServiceSpec> publishedServices,
           final boolean eager,
           @Nonnull final Element element,
           @Nonnull final ServiceRequest[] dependencies )
  {
    assert ( Kind.INPUT == kind && ElementKind.INTERFACE == element.getKind() ) ||
           ( Kind.INJECTABLE == kind && ElementKind.CONSTRUCTOR == element.getKind() ) ||
           ( Kind.PROVIDES == kind && ElementKind.METHOD == element.getKind() );
    _kind = Objects.requireNonNull( kind );
    _id = Objects.requireNonNull( id );
    _publishedServices = Objects.requireNonNull( publishedServices );
    _eager = eager;
    _element = Objects.requireNonNull( element );
    _dependencies = Objects.requireNonNull( dependencies );
    _optional = _publishedServices.stream().anyMatch( ServiceSpec::isOptional );
    // The optionality must match across all published services
    assert ( _optional && _publishedServices.stream().allMatch( ServiceSpec::isOptional ) ) ||
           ( !_optional && _publishedServices.stream().allMatch( ServiceSpec::isRequired ) );
    // Injectables ALWAYS create non-optional bindings
    assert Kind.INJECTABLE != kind || _publishedServices.stream().allMatch( ServiceSpec::isRequired );
  }

  @Nonnull
  Object getOwner()
  {
    assert null != _owner;
    return _owner;
  }

  @Nonnull
  Kind getKind()
  {
    return _kind;
  }

  @Nonnull
  String getId()
  {
    return _id;
  }

  @Nonnull
  List<ServiceSpec> getPublishedServices()
  {
    return _publishedServices;
  }

  boolean isEager()
  {
    return _eager;
  }

  boolean isOptional()
  {
    return _optional;
  }

  boolean isRequired()
  {
    return !isOptional();
  }

  @Nonnull
  Element getElement()
  {
    return _element;
  }

  @Nonnull
  ServiceRequest[] getDependencies()
  {
    return _dependencies;
  }

  void write( @Nonnull final JsonGenerator g )
  {
    g.write( "id", _id );
    if ( !_publishedServices.isEmpty() )
    {
      g.writeStartArray( "publishedServices" );
      for ( final ServiceSpec service : _publishedServices )
      {
        g.writeStartObject();
        service.write( g );
        g.writeEnd();
      }
      g.writeEnd();
    }
    if ( _eager )
    {
      g.write( "eager", _eager );
    }
    if ( _dependencies.length > 0 )
    {
      g.writeStartArray( "dependencies" );
      for ( final ServiceRequest dependency : _dependencies )
      {
        dependency.write( g );
      }
      g.writeEnd();
    }
  }

  void setOwner( @Nonnull final Object owner )
  {
    assert null == _owner;
    assert ( owner instanceof InputDescriptor && Kind.INPUT == _kind ) ||
           ( owner instanceof InjectableDescriptor && Kind.INJECTABLE == _kind ) ||
           ( owner instanceof FragmentDescriptor && Kind.PROVIDES == _kind );
    _owner = owner;
  }

  @Nonnull
  String describe()
  {
    if ( Kind.INPUT == _kind )
    {
      final InputDescriptor input = (InputDescriptor) _owner;
      return ( (TypeElement) _element ).getQualifiedName().toString() +
             "." + input.getName() + "/" + input.getService();
    }
    else if ( Kind.INJECTABLE == _kind )
    {
      return ( (TypeElement) _element.getEnclosingElement() ).getQualifiedName().toString();
    }
    else
    {
      assert Kind.PROVIDES == _kind;
      return ( (TypeElement) _element.getEnclosingElement() ).getQualifiedName().toString() +
             "." +
             _element.getSimpleName();
    }
  }

  @Nonnull
  String getTypeLabel()
  {
    if ( Kind.INPUT == _kind )
    {
      return "[Input]      ";
    }
    else if ( Kind.INJECTABLE == _kind )
    {
      return "[Injectable] ";
    }
    else
    {
      assert Kind.PROVIDES == _kind;
      return "[Provides]   ";
    }
  }

  enum Kind
  {
    /// Instances are passed into injector when it is created
    INPUT,
    /// Instances are created by invoking the constructor
    INJECTABLE,
    /// Instances are created by invoking method in @Fragment annotated type
    PROVIDES
  }
}
