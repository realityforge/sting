package sting.processor;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import org.realityforge.proton.AbstractStandardProcessor;
import org.realityforge.proton.AnnotationsUtil;
import org.realityforge.proton.ElementsUtil;
import org.realityforge.proton.GeneratorUtil;
import org.realityforge.proton.IOUtil;
import org.realityforge.proton.JsonUtil;
import org.realityforge.proton.MemberChecks;
import org.realityforge.proton.ProcessorException;

/**
 * Annotation processor that analyzes sting annotated source and generates dependency injection container.
 */
@SuppressWarnings( "DuplicatedCode" )
@SupportedAnnotationTypes( { Constants.INJECTOR_CLASSNAME,
                             Constants.INJECTABLE_CLASSNAME,
                             Constants.FRAGMENT_CLASSNAME,
                             Constants.DEPENDENCY_CLASSNAME } )
@SupportedSourceVersion( SourceVersion.RELEASE_8 )
@SupportedOptions( { "sting.defer.unresolved",
                     "sting.defer.errors",
                     "sting.emit_json_descriptors",
                     "sting.verbose_out_of_round.errors",
                     "sting.verify_descriptors" } )
public final class StingProcessor
  extends AbstractStandardProcessor
{
  /**
   * Extension for json descriptors.
   */
  static final String JSON_SUFFIX = ".sting.json";
  /**
   * Extension for sting binary descriptors.
   */
  static final String SUFFIX = ".sbf";
  /**
   * Extension for the computed graph descriptor.
   */
  static final String GRAPH_SUFFIX = "__ObjectGraph" + JSON_SUFFIX;
  /**
   * A local cache of bindings that is cleared on error or when processing is complete.
   * This will probably be loaded from json cache files in the future but now we require
   * in memory processing.
   */
  @Nonnull
  private final Registry _registry = new Registry();
  /**
   * Flag controlling whether json descriptors are emitted.
   * Json descriptors are primarily used during debugging and probably should not be enabled in production code.
   */
  private boolean _emitJsonDescriptors;
  /**
   * Flag controlling whether the binary descriptors are deserialized after serialization to verify
   * that they produce the expected output. This is only used for debugging and should not be enabled
   * in production code.
   */
  private boolean _verifyDescriptors;
  /**
   * A utility class for reading and writing the binary descriptors.
   */
  private DescriptorIO _descriptorIO;

  @Nonnull
  @Override
  protected String getIssueTrackerURL()
  {
    return "https://github.com/sting-ioc/sting/issues";
  }

  @Nonnull
  @Override
  protected String getOptionPrefix()
  {
    return "sting";
  }

  @Override
  public synchronized void init( final ProcessingEnvironment processingEnv )
  {
    super.init( processingEnv );
    _descriptorIO = new DescriptorIO( processingEnv.getElementUtils(), processingEnv.getTypeUtils() );
  }

  @SuppressWarnings( "unchecked" )
  @Override
  public boolean process( @Nonnull final Set<? extends TypeElement> annotations, @Nonnull final RoundEnvironment env )
  {
    _emitJsonDescriptors =
      "true".equals( processingEnv.getOptions().getOrDefault( "sting.emit_json_descriptors", "false" ) );
    _verifyDescriptors =
      "true".equals( processingEnv.getOptions().getOrDefault( "sting.verify_descriptors", "false" ) );

    annotations.stream()
      .filter( a -> a.getQualifiedName().toString().equals( Constants.INJECTABLE_CLASSNAME ) )
      .findAny()
      .ifPresent( a -> processTypeElements( env,
                                            (Collection<TypeElement>) env.getElementsAnnotatedWith( a ),
                                            this::processInjectable ) );

    annotations.stream()
      .filter( a -> a.getQualifiedName().toString().equals( Constants.FRAGMENT_CLASSNAME ) )
      .findAny()
      .ifPresent( a -> processTypeElements( env,
                                            (Collection<TypeElement>) env.getElementsAnnotatedWith( a ),
                                            this::processFragment ) );

    annotations.stream()
      .filter( a -> a.getQualifiedName().toString().equals( Constants.DEPENDENCY_CLASSNAME ) )
      .findAny()
      .ifPresent( a -> verifyDependencyElements( env, env.getElementsAnnotatedWith( a ) ) );

    annotations.stream()
      .filter( a -> a.getQualifiedName().toString().equals( Constants.INJECTOR_CLASSNAME ) )
      .findAny()
      .ifPresent( a -> processTypeElements( env,
                                            (Collection<TypeElement>) env.getElementsAnnotatedWith( a ),
                                            this::processInjector ) );

    processResolvedInjectables( env );
    processResolvedFragments( env );
    processResolvedInjectors( env );

    errorIfProcessingOverAndInvalidTypesDetected( env );
    errorIfProcessingOverAndDeferredTypesUnprocessed( env );
    errorIfProcessingOverAndUnprocessedInjectorDetected( env );
    if ( env.processingOver() || env.errorRaised() )
    {
      _registry.clear();
    }
    return true;
  }

  private void errorIfProcessingOverAndUnprocessedInjectorDetected( @Nonnull final RoundEnvironment env )
  {
    if ( env.processingOver() && !env.errorRaised() )
    {
      final List<InjectorDescriptor> injectors = _registry.getInjectors();
      if ( !injectors.isEmpty() )
      {
        processingEnv
          .getMessager()
          .printMessage( Diagnostic.Kind.ERROR,
                         getClass().getSimpleName() + " failed to process " + injectors.size() + " injectors " +
                         "as not all of their dependencies could be resolved. The java code resolved but the " +
                         "descriptors were missing or in the incorrect format. Ensure that the included " +
                         "typed have been compiled with a compatible version of Sting and that the .sbf " +
                         "descriptors have been packaged with the .class files." );
        for ( final InjectorDescriptor injector : injectors )
        {
          processingEnv
            .getMessager()
            .printMessage( Diagnostic.Kind.ERROR,
                           "Failed to process the " + injector.getElement().getQualifiedName() + " injector." );
        }
      }
    }
  }

  private void processResolvedInjectables( @Nonnull final RoundEnvironment env )
  {
    for ( final InjectableDescriptor injectable : new ArrayList<>( _registry.getInjectables() ) )
    {
      performAction( env, e -> {
        if ( isInjectableResolved( injectable ) && !injectable.isJavaStubGenerated() )
        {
          injectable.markJavaStubAsGenerated();
          writeBinaryDescriptor( injectable.getElement(), injectable );
          emitInjectableJsonDescriptor( injectable );
          emitInjectableStub( injectable );
        }
      }, injectable.getElement() );
    }
  }

  private void emitInjectableStub( @Nonnull final InjectableDescriptor injectable )
    throws IOException
  {
    final String packageName = GeneratorUtil.getQualifiedPackageName( injectable.getElement() );
    emitTypeSpec( packageName, InjectableGenerator.buildType( processingEnv, injectable ) );
  }

  private void processResolvedFragments( @Nonnull final RoundEnvironment env )
  {
    for ( final FragmentDescriptor fragment : new ArrayList<>( _registry.getFragments() ) )
    {
      performAction( env, e -> {
        if ( isFragmentResolved( fragment ) && !fragment.isJavaStubGenerated() )
        {
          fragment.markJavaStubAsGenerated();
          writeBinaryDescriptor( fragment.getElement(), fragment );
          emitFragmentJsonDescriptor( fragment );
          emitFragmentStub( fragment );
        }
      }, fragment.getElement() );
    }
  }

  private void emitFragmentStub( @Nonnull final FragmentDescriptor fragment )
    throws IOException
  {
    final String packageName = GeneratorUtil.getQualifiedPackageName( fragment.getElement() );
    emitTypeSpec( packageName, FragmentGenerator.buildType( processingEnv, fragment ) );
  }

  private void processResolvedInjectors( @Nonnull final RoundEnvironment env )
  {
    for ( final InjectorDescriptor injector : new ArrayList<>( _registry.getInjectors() ) )
    {
      performAction( env, e -> {
        if ( isInjectorResolved( injector ) )
        {
          _registry.deregisterInjector( injector );
          buildAndEmitObjectGraph( injector );
        }
      }, injector.getElement() );
    }
  }

  private void buildAndEmitObjectGraph( @Nonnull final InjectorDescriptor injector )
    throws Exception
  {
    final ObjectGraph graph = new ObjectGraph( injector );
    registerIncludesComponents( graph );

    buildObjectGraphNodes( graph );

    if ( graph.getNodes().isEmpty() && graph.getRootNode().getDependsOn().isEmpty() )
    {
      throw new ProcessorException( MemberChecks.toSimpleName( Constants.INJECTOR_CLASSNAME ) + " target " +
                                    "produced an empty object graph. This means that there are no eager nodes " +
                                    "in the includes and there are no dependencies or only unsatisfied optional " +
                                    "dependencies defined by the injector",
                                    graph.getInjector().getElement() );
    }

    propagateEagerFlagUpstream( graph );

    CircularDependencyChecker.verifyNoCircularDependencyLoops( graph );

    emitObjectGraphJsonDescriptor( graph );

    final String packageName = GeneratorUtil.getQualifiedPackageName( graph.getInjector().getElement() );
    emitTypeSpec( packageName, InjectorGenerator.buildType( processingEnv, graph ) );
  }

  private void propagateEagerFlagUpstream( @Nonnull final ObjectGraph graph )
  {
    // Propagate Eager flag to all dependencies of eager nodes breaking the propagation at Supplier nodes
    // They may not be configured as eager but they are effectively eager given that they will be created
    // at startup, they may as well be marked as eager objects as that results in smaller code-size.
    graph.getNodes().stream().filter( n -> n.getBinding().isEager() ).forEach( Node::markNodeAndUpstreamAsEager );
  }

  private void registerIncludesComponents( @Nonnull final ObjectGraph graph )
  {
    registerIncludes( graph, graph.getInjector().getIncludes() );
  }

  private void registerIncludes( @Nonnull final ObjectGraph graph,
                                 @Nonnull final Collection<DeclaredType> includes )
  {
    for ( final DeclaredType include : includes )
    {
      final TypeElement element = (TypeElement) include.asElement();
      if ( AnnotationsUtil.hasAnnotationOfType( element, Constants.FRAGMENT_CLASSNAME ) )
      {
        final FragmentDescriptor fragment = _registry.getFragmentByClassName( element.getQualifiedName().toString() );
        registerIncludes( graph, fragment.getIncludes() );
        graph.registerFragment( fragment );
      }
      else
      {
        assert AnnotationsUtil.hasAnnotationOfType( element, Constants.INJECTABLE_CLASSNAME );
        final InjectableDescriptor injectable =
          _registry.getInjectableByClassName( element.getQualifiedName().toString() );
        final Binding binding = injectable.getBinding();
        if ( binding.isEager() )
        {
          graph.findOrCreateNode( binding );
        }
        graph.registerInjectable( injectable );
      }
    }
  }

  private void buildObjectGraphNodes( @Nonnull final ObjectGraph graph )
  {
    final InjectorDescriptor injector = graph.getInjector();
    final Node rootNode = graph.getRootNode();
    final Set<Node> completed = new HashSet<>();
    final Stack<WorkEntry> workList = new Stack<>();
    addDependsOnToWorkList( workList, rootNode, null );
    while ( !workList.isEmpty() )
    {
      final WorkEntry workEntry = workList.pop();
      final Edge edge = workEntry.getEntry().getEdge();
      assert null != edge;
      final DependencyDescriptor dependency = edge.getDependency();
      final Coordinate coordinate = dependency.getCoordinate();
      final List<Binding> bindings = new ArrayList<>( graph.findAllBindingsByCoordinate( coordinate ) );

      if ( bindings.isEmpty() )
      {
        final String classname = coordinate.getType().toString();
        final InjectableDescriptor injectable = _registry.findInjectableByClassName( classname );
        if ( null != injectable && injectable.getBinding().getCoordinates().contains( coordinate ) )
        {
          bindings.add( injectable.getBinding() );
        }
        if ( bindings.isEmpty() )
        {
          final TypeElement typeElement = processingEnv.getElementUtils().getTypeElement( classname );
          final byte[] data = tryLoadDescriptorData( typeElement );
          if ( null != data )
          {
            try
            {
              final Object descriptor = loadDescriptor( classname, data );
              if ( descriptor instanceof InjectableDescriptor )
              {
                final InjectableDescriptor injectableDescriptor = (InjectableDescriptor) descriptor;
                _registry.registerInjectable( injectableDescriptor );
                bindings.add( injectableDescriptor.getBinding() );
              }
            }
            catch ( final IOException e )
            {
              final Node node = edge.getNode();
              final Object owner = node.hasNoBinding() ? null : node.getBinding().getOwner();
              final TypeElement ownerElement =
                owner instanceof FragmentDescriptor ? ( (FragmentDescriptor) owner ).getElement() :
                owner instanceof InjectableDescriptor ? ( (InjectableDescriptor) owner ).getElement() :
                injector.getElement();
              throw new ProcessorException( "Failed to read the Sting descriptor for " +
                                            "type " + classname + ". Error: " + e,
                                            ownerElement );
            }
          }
        }
      }

      final List<Binding> nullableProviders = bindings.stream()
        .filter( b -> Binding.Kind.NULLABLE_PROVIDES == b.getKind() )
        .collect( Collectors.toList() );
      if ( !dependency.isOptional() && !nullableProviders.isEmpty() )
      {
        final String message =
          MemberChecks.mustNot( Constants.INJECTOR_CLASSNAME,
                                "contain a nullable " +
                                MemberChecks.toSimpleName( Constants.PROVIDES_CLASSNAME ) +
                                " method and a non-optional dependency " + coordinate +
                                " with the same coordinate.\n" +
                                "Dependency Path:\n" + workEntry.describePathFromRoot() + "\n" +
                                "Binding" + ( nullableProviders.size() > 1 ? "s" : "" ) + ":\n" +
                                bindingsToString( nullableProviders ) );
        throw new ProcessorException( message, dependency.getElement() );
      }
      if ( bindings.isEmpty() )
      {
        if ( dependency.isOptional() || dependency.getKind().isCollection() )
        {
          edge.setSatisfiedBy( Collections.emptyList() );
        }
        else
        {
          final String message =
            MemberChecks.mustNot( Constants.INJECTOR_CLASSNAME,
                                  "contain a non-optional dependency " + coordinate +
                                  " that can not be satisfied.\n" +
                                  "Dependency Path:\n" + workEntry.describePathFromRoot() );
          throw new ProcessorException( message, dependency.getElement() );
        }
      }
      else
      {
        final DependencyDescriptor.Kind kind = dependency.getKind();
        if ( 1 == bindings.size() || kind.isCollection() )
        {
          final List<Node> nodes = new ArrayList<>();
          for ( final Binding binding : bindings )
          {
            final String id = binding.getId();
            final Node existing = graph.findNodeById( id );
            if ( null != existing && binding != existing.getBinding() )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTOR_CLASSNAME,
                                                                  "contain multiple nodes with the id '" +
                                                                  id + "'.\nPath:\n" +
                                                                  workEntry.describePathFromRoot() ),
                                            existing.getBinding().getElement().getEnclosingElement() );
            }
            final Node node = graph.findOrCreateNode( binding );
            nodes.add( node );
            if ( !completed.contains( node ) )
            {
              completed.add( node );
              addDependsOnToWorkList( workList, node, workEntry );
            }
          }
          edge.setSatisfiedBy( nodes );
        }
        else
        {
          //noinspection ConstantConditions
          assert bindings.size() > 1 && !kind.isCollection();
          final String message =
            MemberChecks.mustNot( Constants.INJECTOR_CLASSNAME,
                                  "contain a non-collection dependency " + coordinate +
                                  " that can be satisfied by multiple nodes.\n" +
                                  "Dependency Path:\n" + workEntry.describePathFromRoot() + "\n" +
                                  "Candidate Nodes:\n" + bindingsToString( bindings ) );
          throw new ProcessorException( message, dependency.getElement() );
        }
      }
    }
    graph.complete();
  }

  @Nonnull
  private String bindingsToString( @Nonnull final List<Binding> bindings )
  {
    return bindings
      .stream()
      .map( b -> "  " + b.getTypeLabel() + "    " + b.describe() )
      .collect( Collectors.joining( "\n" ) );
  }

  private void addDependsOnToWorkList( @Nonnull final Stack<WorkEntry> workList,
                                       @Nonnull final Node node,
                                       @Nullable final WorkEntry parent )
  {
    for ( final Edge e : node.getDependsOn() )
    {
      final Stack<PathEntry> stack = new Stack<>();
      if ( null != parent )
      {
        stack.addAll( parent.getStack() );
      }
      final PathEntry entry = new PathEntry( node, e );
      stack.add( entry );
      workList.add( new WorkEntry( entry, stack ) );
    }
  }

  private void emitObjectGraphJsonDescriptor( @Nonnull final ObjectGraph graph )
    throws IOException
  {
    if ( _emitJsonDescriptors )
    {
      final TypeElement element = graph.getInjector().getElement();
      final String filename = toFilename( element ) + GRAPH_SUFFIX;
      JsonUtil.writeJsonResource( processingEnv, element, filename, graph::write );
    }
  }

  private boolean isInjectableResolved( @Nonnull final InjectableDescriptor injectable )
  {
    return isResolved( injectable.getElement(), Collections.emptyList() );
  }

  private boolean isFragmentResolved( @Nonnull final FragmentDescriptor fragment )
  {
    return isResolved( fragment.getElement(), fragment.getIncludes() );
  }

  private boolean isInjectorResolved( @Nonnull final InjectorDescriptor injector )
  {
    return isResolved( injector.getElement(), injector.getIncludes() );
  }

  private boolean isResolved( @Nonnull final TypeElement originator, @Nonnull final Collection<DeclaredType> includes )
  {
    // By the time we get here we can guarantee that the java types are correctly resolved
    // so we only have to check that the descriptors are present and valid in this method
    for ( final DeclaredType include : includes )
    {
      final TypeElement element = (TypeElement) include.asElement();
      final String classname = element.getQualifiedName().toString();
      if ( null == _registry.findFragmentByClassName( classname ) &&
           null == _registry.findInjectableByClassName( classname ) )
      {
        final byte[] data = tryLoadDescriptorData( element );
        if ( null == data )
        {
          return false;
        }
        try
        {
          final Object descriptor = loadDescriptor( classname, data );
          if ( descriptor instanceof FragmentDescriptor )
          {
            _registry.registerFragment( (FragmentDescriptor) descriptor );
          }
          else
          {
            _registry.registerInjectable( (InjectableDescriptor) descriptor );
          }
        }
        catch ( final IOException e )
        {
          throw new ProcessorException( "Failed to read the Sting descriptor for " +
                                        "include: " + classname + ". " +
                                        "Error: " + e,
                                        originator );
        }
      }
    }
    return true;
  }

  private void processInjector( @Nonnull final TypeElement element )
    throws Exception
  {
    final ElementKind kind = element.getKind();
    if ( ElementKind.INTERFACE != kind )
    {
      throw new ProcessorException( MemberChecks.must( Constants.INJECTOR_CLASSNAME, "be an interface" ),
                                    element );
    }
    if ( !element.getTypeParameters().isEmpty() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTOR_CLASSNAME, "have type parameters" ),
                                    element );
    }

    final List<DeclaredType> includes = extractIncludes( element, Constants.INJECTOR_CLASSNAME );

    final List<DependencyDescriptor> topLevelDependencies = new ArrayList<>();
    final List<ExecutableElement> methods =
      ElementsUtil.getMethods( element, processingEnv.getElementUtils(), processingEnv.getTypeUtils() );
    for ( final ExecutableElement method : methods )
    {
      if ( method.getModifiers().contains( Modifier.ABSTRACT ) )
      {
        processInjectorDependencyMethod( topLevelDependencies, method );
      }
    }
    final InjectorDescriptor injector = new InjectorDescriptor( element, includes, topLevelDependencies );
    _registry.registerInjector( injector );
    emitInjectorJsonDescriptor( injector );
  }

  private void emitInjectorJsonDescriptor( @Nonnull final InjectorDescriptor injector )
    throws IOException
  {
    if ( _emitJsonDescriptors )
    {
      final TypeElement element = injector.getElement();
      final String filename = toFilename( element ) + JSON_SUFFIX;
      JsonUtil.writeJsonResource( processingEnv, element, filename, injector::write );
    }
  }

  private void processInjectorDependencyMethod( @Nonnull final List<DependencyDescriptor> topLevelDependencies,
                                                @Nonnull final ExecutableElement method )
  {
    assert method.getModifiers().contains( Modifier.ABSTRACT );
    MemberChecks.mustReturnAValue( Constants.DEPENDENCY_CLASSNAME, method );
    MemberChecks.mustNotHaveAnyParameters( Constants.DEPENDENCY_CLASSNAME, method );
    MemberChecks.mustNotHaveAnyTypeParameters( Constants.DEPENDENCY_CLASSNAME, method );
    topLevelDependencies.add( processDependencyMethod( method ) );
  }

  @Nonnull
  private DependencyDescriptor processDependencyMethod( @Nonnull final ExecutableElement method )
  {
    final TypeMirror returnType = method.getReturnType();
    final boolean optional =
      AnnotationsUtil.hasAnnotationOfType( method, GeneratorUtil.NULLABLE_ANNOTATION_CLASSNAME );
    final AnnotationMirror annotation =
      AnnotationsUtil.findAnnotationByType( method, Constants.DEPENDENCY_CLASSNAME );
    final String qualifier =
      null == annotation ? "" : AnnotationsUtil.getAnnotationValue( annotation, "qualifier" );

    final boolean isDeclaredType = TypeKind.DECLARED == returnType.getKind();
    final DeclaredType declaredType = isDeclaredType ? (DeclaredType) returnType : null;
    final boolean isParameterizedType = isDeclaredType && !declaredType.getTypeArguments().isEmpty();
    final DependencyDescriptor.Kind type;
    final TypeMirror dependencyType;
    if ( TypeKind.ARRAY == returnType.getKind() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME, "return an array type" ),
                                    method );
    }
    else if ( null == declaredType )
    {
      type = DependencyDescriptor.Kind.INSTANCE;
      dependencyType = returnType;
    }
    else if ( !isParameterizedType )
    {
      if ( Supplier.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "return a raw " +
                                                            Supplier.class.getCanonicalName() +
                                                            " type" ),
                                      method );
      }
      else if ( Collection.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "return a a raw " +
                                                            Collection.class.getCanonicalName() + " type" ),
                                      method );
      }
      else if ( !( (TypeElement) declaredType.asElement() ).getTypeParameters().isEmpty() )
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "return a raw parameterized type. Parameterized " +
                                                            "types are only permitted for specific types such as " +
                                                            Supplier.class.getCanonicalName() + " and " +
                                                            Collection.class.getCanonicalName() ),
                                      method );
      }
      type = DependencyDescriptor.Kind.INSTANCE;
      dependencyType = returnType;
    }
    else
    {
      if ( Supplier.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        final TypeMirror typeArgument = declaredType.getTypeArguments().get( 0 );
        if ( TypeKind.WILDCARD == typeArgument.getKind() )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "return a " + Supplier.class.getCanonicalName() +
                                                              " type with a wildcard type parameter" ),
                                        method );
        }
        type = DependencyDescriptor.Kind.SUPPLIER;
        dependencyType = typeArgument;
      }
      else if ( Collection.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        final TypeMirror typeArgument = declaredType.getTypeArguments().get( 0 );
        if ( TypeKind.WILDCARD == typeArgument.getKind() )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "return a " + Collection.class.getCanonicalName() +
                                                              " type with a wildcard type parameter" ),
                                        method );
        }
        else if ( TypeKind.DECLARED == typeArgument.getKind() &&
                  Supplier.class.getCanonicalName().equals( getClassname( (DeclaredType) typeArgument ) ) )
        {
          final DeclaredType supplierType = (DeclaredType) typeArgument;
          final List<? extends TypeMirror> nestedTypeArguments = supplierType.getTypeArguments();
          if ( nestedTypeArguments.isEmpty() )
          {
            throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                "return a supplier collection parameter that " +
                                                                "contains a raw " +
                                                                Supplier.class.getCanonicalName() + " type" ),
                                          method );
          }
          else
          {
            final TypeMirror nestedParameterType = nestedTypeArguments.get( 0 );
            if ( TypeKind.WILDCARD == nestedParameterType.getKind() )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                  "return a supplier collection parameter with a wildcard type parameter" ),
                                            method );
            }
            else if ( TypeKind.DECLARED == nestedParameterType.getKind() &&
                      isParameterized( (DeclaredType) nestedParameterType ) )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                  "return a a supplier collection type that contains a parameterized type" ),
                                            method );
            }
            else
            {
              type = DependencyDescriptor.Kind.SUPPLIER_COLLECTION;
              dependencyType = nestedParameterType;
            }
          }
        }
        else if ( TypeKind.DECLARED == typeArgument.getKind() && isParameterized( (DeclaredType) typeArgument ) )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "return a collection type that contains a parameterized type" ),
                                        method );
        }
        else
        {
          type = DependencyDescriptor.Kind.COLLECTION;
          dependencyType = typeArgument;
        }
      }
      else
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "return a value that is a parameterized type. " +
                                                            "This is only permitted for specific types such as " +
                                                            Supplier.class.getCanonicalName() + " and " +
                                                            Collection.class.getCanonicalName() ),
                                      method );
      }
    }

    if ( optional && type.isCollection() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                          "be annotated with @Nullable and be a collection type" ),
                                    method );
    }

    final Coordinate coordinate = new Coordinate( qualifier, dependencyType );
    return new DependencyDescriptor( type, coordinate, optional, method, -1 );
  }

  private void verifyDependencyElements( @Nonnull final RoundEnvironment env,
                                         @Nonnull final Set<? extends Element> elements )
  {
    for ( final Element element : elements )
    {
      if ( ElementKind.PARAMETER == element.getKind() )
      {
        final Element executableElement = element.getEnclosingElement();
        final boolean injectableType =
          AnnotationsUtil.hasAnnotationOfType( executableElement.getEnclosingElement(),
                                               Constants.INJECTABLE_CLASSNAME );
        final boolean isFragmentType =
          AnnotationsUtil.hasAnnotationOfType( executableElement.getEnclosingElement(), Constants.FRAGMENT_CLASSNAME );
        final ElementKind executableKind = executableElement.getKind();
        if ( !injectableType && ElementKind.CONSTRUCTOR == executableKind )
        {
          reportError( env,
                       MemberChecks.must( Constants.DEPENDENCY_CLASSNAME,
                                          "only be present on a parameter of a constructor " +
                                          "if the enclosing type is annotated with " +
                                          MemberChecks.toSimpleName( Constants.INJECTABLE_CLASSNAME ) ),
                       element );
        }
        else if ( !isFragmentType && ElementKind.METHOD == executableKind )
        {
          reportError( env,
                       MemberChecks.must( Constants.DEPENDENCY_CLASSNAME,
                                          "only be present on a parameter of a method " +
                                          "if the enclosing type is annotated with " +
                                          MemberChecks.toSimpleName( Constants.FRAGMENT_CLASSNAME ) ),
                       element );
        }
        else
        {
          assert ( injectableType && ElementKind.CONSTRUCTOR == executableKind ) ||
                 ( isFragmentType && ElementKind.METHOD == executableKind );
        }
      }
      else
      {
        assert ElementKind.METHOD == element.getKind();
        if ( !AnnotationsUtil.hasAnnotationOfType( element.getEnclosingElement(), Constants.INJECTOR_CLASSNAME ) )
        {
          reportError( env,
                       MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                             "be a method unless present in a type annotated with " +
                                             MemberChecks.toSimpleName( Constants.INJECTOR_CLASSNAME ) ),
                       element );
        }
      }
    }
  }

  private void processFragment( @Nonnull final TypeElement element )
  {
    if ( ElementKind.INTERFACE != element.getKind() )
    {
      throw new ProcessorException( MemberChecks.must( Constants.FRAGMENT_CLASSNAME, "be an interface" ),
                                    element );
    }
    else if ( !element.getTypeParameters().isEmpty() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.FRAGMENT_CLASSNAME, "have type parameters" ),
                                    element );
    }
    else if ( !element.getInterfaces().isEmpty() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.FRAGMENT_CLASSNAME, "extend any interfaces" ),
                                    element );
    }
    final List<DeclaredType> includes = extractIncludes( element, Constants.FRAGMENT_CLASSNAME );
    final Map<ExecutableElement, Binding> bindings = new LinkedHashMap<>();
    final List<ExecutableElement> methods =
      ElementsUtil.getMethods( element, processingEnv.getElementUtils(), processingEnv.getTypeUtils() );
    for ( final ExecutableElement method : methods )
    {
      processProvidesMethod( element, bindings, method );
    }
    if ( bindings.isEmpty() && includes.isEmpty() )
    {
      throw new ProcessorException( MemberChecks.must( Constants.FRAGMENT_CLASSNAME,
                                                       "contain one or more methods or one or more includes" ),
                                    element );
    }
    final FragmentDescriptor fragment = new FragmentDescriptor( element, includes, bindings.values() );
    _registry.registerFragment( fragment );
  }

  @Nonnull
  private List<DeclaredType> extractIncludes( @Nonnull final TypeElement element,
                                              @Nonnull final String annotationClassname )
  {
    final List<DeclaredType> results = new ArrayList<>();
    final List<TypeMirror> includes =
      AnnotationsUtil.getTypeMirrorsAnnotationParameter( element, annotationClassname, "includes" );
    for ( final TypeMirror include : includes )
    {
      final Element includeElement = processingEnv.getTypeUtils().asElement( include );
      if ( !AnnotationsUtil.hasAnnotationOfType( includeElement, Constants.FRAGMENT_CLASSNAME ) &&
           !AnnotationsUtil.hasAnnotationOfType( includeElement, Constants.INJECTABLE_CLASSNAME ) )
      {
        throw new ProcessorException( MemberChecks.toSimpleName( annotationClassname ) + " target has an " +
                                      "includes parameter containing the value " + include + " that is not a type " +
                                      "annotated by either " +
                                      MemberChecks.toSimpleName( Constants.FRAGMENT_CLASSNAME ) + " or " +
                                      MemberChecks.toSimpleName( Constants.INJECTABLE_CLASSNAME ),
                                      element );
      }
      else
      {
        results.add( (DeclaredType) include );
      }
    }
    return results;
  }

  private void emitFragmentJsonDescriptor( @Nonnull final FragmentDescriptor fragment )
    throws IOException
  {
    if ( _emitJsonDescriptors )
    {
      final TypeElement element = fragment.getElement();
      final String filename = toFilename( element ) + JSON_SUFFIX;
      JsonUtil.writeJsonResource( processingEnv, element, filename, fragment::write );
    }
  }

  private void processProvidesMethod( @Nonnull final TypeElement element,
                                      @Nonnull final Map<ExecutableElement, Binding> bindings,
                                      @Nonnull final ExecutableElement method )
  {
    MemberChecks.mustReturnAValue( Constants.PROVIDES_CLASSNAME, method );
    MemberChecks.mustNotHaveAnyTypeParameters( Constants.PROVIDES_CLASSNAME, method );
    if ( !method.getModifiers().contains( Modifier.DEFAULT ) )
    {
      throw new ProcessorException( MemberChecks.must( Constants.PROVIDES_CLASSNAME, "have a default modifier" ),
                                    method );
    }
    else
    {
      final boolean providesPresent = AnnotationsUtil.hasAnnotationOfType( method, Constants.PROVIDES_CLASSNAME );
      final boolean nullablePresent =
        AnnotationsUtil.hasAnnotationOfType( method, GeneratorUtil.NULLABLE_ANNOTATION_CLASSNAME );

      if ( nullablePresent && method.getReturnType().getKind().isPrimitive() )
      {
        throw new ProcessorException( MemberChecks.toSimpleName( Constants.PROVIDES_CLASSNAME ) +
                                      " target is incorrectly annotated with " +
                                      MemberChecks.toSimpleName( GeneratorUtil.NULLABLE_ANNOTATION_CLASSNAME ) +
                                      " as the return type is a primitive",
                                      method );
      }
      final List<TypeMirror> types =
        providesPresent ?
        AnnotationsUtil.getTypeMirrorsAnnotationParameter( method, Constants.PROVIDES_CLASSNAME, "types" ) :
        Collections.emptyList();
      final List<TypeMirror> publishedTypes;
      if ( !providesPresent || isDefaultTypes( types ) )
      {
        publishedTypes = Collections.singletonList( method.getReturnType() );
      }
      else
      {
        for ( final TypeMirror type : types )
        {
          if ( !processingEnv.getTypeUtils().isAssignable( method.getReturnType(), type ) )
          {
            throw new ProcessorException( MemberChecks.toSimpleName( Constants.PROVIDES_CLASSNAME ) +
                                          " target has a type parameter containing the value " + type +
                                          " that is not assignable to the return type of the method",
                                          method );
          }
        }
        publishedTypes = types;
      }
      final String qualifier =
        providesPresent ?
        (String) AnnotationsUtil.getAnnotationValue( method, Constants.PROVIDES_CLASSNAME, "qualifier" )
          .getValue() :
        "";
      final boolean eager =
        providesPresent &&
        (boolean) AnnotationsUtil.getAnnotationValue( method, Constants.PROVIDES_CLASSNAME, "eager" ).getValue();
      final String declaredId =
        providesPresent ?
        (String) AnnotationsUtil.getAnnotationValue( method, Constants.PROVIDES_CLASSNAME, "id" ).getValue() :
        "";
      final String id = declaredId.isEmpty() ? element.getQualifiedName() + "#" + method.getSimpleName() : declaredId;

      final List<DependencyDescriptor> dependencies = new ArrayList<>();
      int index = 0;
      final List<? extends TypeMirror> parameterTypes = ( (ExecutableType) method.asType() ).getParameterTypes();
      for ( final VariableElement parameter : method.getParameters() )
      {
        dependencies.add( processFragmentDependencyParameter( parameter, parameterTypes.get( index ), index ) );
        index++;
      }
      if ( publishedTypes.isEmpty() && !eager )
      {
        throw new ProcessorException( MemberChecks.must( Constants.PROVIDES_CLASSNAME,
                                                         "have one or more types specified or must specify eager = true otherwise the binding will never be used by the injector" ),
                                      element );
      }
      bindings.entrySet()
        .stream()
        .filter( e -> e.getValue().getId().equals( id ) )
        .map( Map.Entry::getKey )
        .findAny()
        .ifPresent( matchingMethod -> {
          throw new ProcessorException( MemberChecks.must( Constants.PROVIDES_CLASSNAME,
                                                           "have a unique id but it has the same id as the method named " +
                                                           matchingMethod.getSimpleName() ),
                                        element );

        } );

      final Binding binding =
        new Binding( nullablePresent ? Binding.Kind.NULLABLE_PROVIDES : Binding.Kind.PROVIDES,
                     id,
                     qualifier,
                     publishedTypes.toArray( new TypeMirror[ 0 ] ),
                     eager,
                     method,
                     dependencies.toArray( new DependencyDescriptor[ 0 ] ) );
      bindings.put( method, binding );
    }
  }

  @Nonnull
  private DependencyDescriptor processFragmentDependencyParameter( @Nonnull final VariableElement parameter,
                                                                   @Nonnull final TypeMirror parameterType,
                                                                   final int parameterIndex )
  {
    final boolean optional =
      AnnotationsUtil.hasAnnotationOfType( parameter, GeneratorUtil.NULLABLE_ANNOTATION_CLASSNAME );
    final AnnotationMirror annotation =
      AnnotationsUtil.findAnnotationByType( parameter, Constants.DEPENDENCY_CLASSNAME );
    final String qualifier =
      null == annotation ? "" : AnnotationsUtil.getAnnotationValue( annotation, "qualifier" );

    final boolean isDeclaredType = TypeKind.DECLARED == parameterType.getKind();
    final DeclaredType declaredType = isDeclaredType ? (DeclaredType) parameterType : null;
    final boolean isParameterizedType = isDeclaredType && !declaredType.getTypeArguments().isEmpty();
    final DependencyDescriptor.Kind type;
    final TypeMirror dependencyType;
    if ( TypeKind.ARRAY == parameterType.getKind() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME, "be an array type" ),
                                    parameter );
    }
    else if ( null == declaredType )
    {
      type = DependencyDescriptor.Kind.INSTANCE;
      dependencyType = parameterType;
    }
    else if ( !isParameterizedType )
    {
      if ( !( (TypeElement) declaredType.asElement() ).getTypeParameters().isEmpty() )
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "be a raw parameterized type" ),
                                      parameter );
      }
      type = DependencyDescriptor.Kind.INSTANCE;
      dependencyType = parameterType;
    }
    else
    {
      if ( Supplier.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        final TypeMirror typeArgument = declaredType.getTypeArguments().get( 0 );
        if ( TypeKind.WILDCARD == typeArgument.getKind() )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "be a parameterized type with a wildcard type parameter" ),
                                        parameter );
        }
        type = DependencyDescriptor.Kind.SUPPLIER;
        dependencyType = typeArgument;
      }
      else if ( Collection.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        final TypeMirror typeArgument = declaredType.getTypeArguments().get( 0 );
        if ( TypeKind.WILDCARD == typeArgument.getKind() )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "be a parameterized type with a wildcard type parameter" ),
                                        parameter );
        }
        else if ( TypeKind.DECLARED == typeArgument.getKind() &&
                  Supplier.class.getCanonicalName().equals( getClassname( (DeclaredType) typeArgument ) ) )
        {
          final DeclaredType supplierType = (DeclaredType) typeArgument;
          final List<? extends TypeMirror> nestedTypeArguments = supplierType.getTypeArguments();
          if ( nestedTypeArguments.isEmpty() )
          {
            throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                "be a raw supplier collection type" ),
                                          parameter );
          }
          else
          {
            final TypeMirror nestedParameterType = nestedTypeArguments.get( 0 );
            if ( TypeKind.WILDCARD == nestedParameterType.getKind() )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                  "be a supplier collection with a wildcard type parameter" ),
                                            parameter );
            }
            else if ( TypeKind.DECLARED == nestedParameterType.getKind() &&
                      isParameterized( (DeclaredType) nestedParameterType ) )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                  "be a supplier collection with a parameterized type as the type parameter" ),
                                            parameter );
            }
            else
            {
              type = DependencyDescriptor.Kind.SUPPLIER_COLLECTION;
              dependencyType = nestedParameterType;
            }
          }
        }
        else if ( TypeKind.DECLARED == typeArgument.getKind() && isParameterized( (DeclaredType) typeArgument ) )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "be a collection parameter that contains a parameterized type as the type parameter" ),
                                        parameter );
        }
        else
        {
          type = DependencyDescriptor.Kind.COLLECTION;
          dependencyType = typeArgument;
        }
      }
      else
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "be a parameterized type other than the special " +
                                                            "types known by the framework such as " +
                                                            Supplier.class.getCanonicalName() + " and " +
                                                            Collection.class.getCanonicalName() ),
                                      parameter );
      }
    }

    if ( optional && type.isCollection() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                          "be annotated with @Nullable and be a collection type" ),
                                    parameter );
    }

    final Coordinate coordinate = new Coordinate( qualifier, dependencyType );
    return new DependencyDescriptor( type, coordinate, optional, parameter, parameterIndex );
  }

  private void processInjectable( @Nonnull final TypeElement element )
  {
    if ( ElementKind.CLASS != element.getKind() )
    {
      throw new ProcessorException( MemberChecks.must( Constants.INJECTABLE_CLASSNAME, "be a class" ),
                                    element );
    }
    else if ( element.getModifiers().contains( Modifier.ABSTRACT ) )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTABLE_CLASSNAME, "be abstract" ),
                                    element );
    }
    else if ( ElementsUtil.isNonStaticNestedClass( element ) )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTABLE_CLASSNAME,
                                                          "be a non-static nested class" ),
                                    element );
    }
    else if ( !element.getTypeParameters().isEmpty() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTABLE_CLASSNAME, "have type parameters" ),
                                    element );
    }
    final List<TypeMirror> types =
      AnnotationsUtil.getTypeMirrorsAnnotationParameter( element, Constants.INJECTABLE_CLASSNAME, "types" );
    final List<TypeMirror> publishedTypes;
    if ( isDefaultTypes( types ) )
    {
      publishedTypes = Collections.singletonList( element.asType() );
    }
    else
    {
      for ( final TypeMirror type : types )
      {
        if ( !processingEnv.getTypeUtils().isAssignable( element.asType(), type ) )
        {
          throw new ProcessorException( MemberChecks.toSimpleName( Constants.INJECTABLE_CLASSNAME ) +
                                        " target has a type parameter containing the value " + type +
                                        " that is not assignable to the declaring type",
                                        element );
        }
      }
      publishedTypes = types;
    }
    final List<ExecutableElement> constructors = ElementsUtil.getConstructors( element );
    final ExecutableElement constructor = constructors.get( 0 );
    if ( constructors.size() > 1 )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTABLE_CLASSNAME,
                                                          "have multiple constructors" ),
                                    element );
    }
    injectableConstructorMustNotBeProtected( constructor );
    injectableConstructorMustNotBePublic( constructor );

    final String declaredId =
      (String) AnnotationsUtil.getAnnotationValue( element, Constants.INJECTABLE_CLASSNAME, "id" ).getValue();
    final String id = declaredId.isEmpty() ? element.getQualifiedName().toString() : declaredId;
    final String qualifier =
      (String) AnnotationsUtil.getAnnotationValue( element, Constants.INJECTABLE_CLASSNAME, "qualifier" ).getValue();
    final boolean eager =
      (boolean) AnnotationsUtil.getAnnotationValue( element, Constants.INJECTABLE_CLASSNAME, "eager" ).getValue();

    final List<DependencyDescriptor> dependencies = new ArrayList<>();
    int index = 0;
    final List<? extends TypeMirror> parameterTypes = ( (ExecutableType) constructor.asType() ).getParameterTypes();
    for ( final VariableElement parameter : constructor.getParameters() )
    {
      dependencies.add( handleConstructorParameter( parameter, parameterTypes.get( index ), index ) );
      index++;
    }
    if ( publishedTypes.isEmpty() && !eager )
    {
      throw new ProcessorException( MemberChecks.must( Constants.INJECTABLE_CLASSNAME,
                                                       "have one or more types specified or must specify eager = true otherwise the binding will never be used by the injector" ),
                                    element );
    }
    final Binding binding =
      new Binding( Binding.Kind.INJECTABLE,
                   id,
                   qualifier,
                   publishedTypes.toArray( new TypeMirror[ 0 ] ),
                   eager,
                   constructor,
                   dependencies.toArray( new DependencyDescriptor[ 0 ] ) );
    final InjectableDescriptor injectable = new InjectableDescriptor( binding );
    _registry.registerInjectable( injectable );
  }

  private void writeBinaryDescriptor( @Nonnull final TypeElement element,
                                      @Nonnull final Object descriptor )
    throws IOException
  {
    final String[] nameParts = extractNameParts( element );

    // Write out the descriptor
    final FileObject resource =
      processingEnv.getFiler().createResource( StandardLocation.CLASS_OUTPUT, nameParts[ 0 ], nameParts[ 1 ], element );
    try ( final OutputStream out = resource.openOutputStream() )
    {
      try ( final DataOutputStream dos1 = new DataOutputStream( out ) )
      {
        _descriptorIO.write( dos1, descriptor );
      }
    }

    if ( _verifyDescriptors )
    {
      verifyDescriptor( element, descriptor );
    }
  }

  @Nonnull
  private String[] extractNameParts( @Nonnull final TypeElement element )
  {
    final String binaryName = processingEnv.getElementUtils().getBinaryName( element ).toString();
    final int lastIndex = binaryName.lastIndexOf( "." );
    final String packageName = -1 == lastIndex ? "" : binaryName.substring( 0, lastIndex );
    final String relativeName = binaryName.substring( -1 == lastIndex ? 0 : lastIndex + 1 ) + SUFFIX;

    return new String[]{ packageName, relativeName };
  }

  private void verifyDescriptor( @Nonnull final TypeElement element, @Nonnull final Object descriptor )
    throws IOException
  {
    final ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    try ( final DataOutputStream dos = new DataOutputStream( baos1 ) )
    {
      _descriptorIO.write( dos, descriptor );
    }
    final Object newDescriptor;
    try ( final DataInputStream dos = new DataInputStream( new ByteArrayInputStream( baos1.toByteArray() ) ) )
    {
      newDescriptor = _descriptorIO.read( dos, element.getQualifiedName().toString() );
    }
    final ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    try ( final DataOutputStream dos = new DataOutputStream( baos2 ) )
    {
      _descriptorIO.write( dos, newDescriptor );
    }

    if ( !Arrays.equals( baos1.toByteArray(), baos2.toByteArray() ) )
    {
      throw new ProcessorException( "Failed to emit valid binary descriptor for " + element.getQualifiedName() +
                                    ". Reading the emitted descriptor did not produce an equivalent descriptor.",
                                    element );
    }
  }

  private void emitInjectableJsonDescriptor( @Nonnull final InjectableDescriptor injectable )
    throws IOException
  {
    if ( _emitJsonDescriptors )
    {
      final TypeElement element = injectable.getElement();
      final String filename = toFilename( element ) + JSON_SUFFIX;
      JsonUtil.writeJsonResource( processingEnv, element, filename, injectable::write );
    }
  }

  @Nonnull
  private String toFilename( @Nonnull final TypeElement typeElement )
  {
    return GeneratorUtil.getGeneratedClassName( typeElement, "", "" ).toString().replace( ".", "/" );
  }

  @Nonnull
  private DependencyDescriptor handleConstructorParameter( @Nonnull final VariableElement parameter,
                                                           @Nonnull final TypeMirror parameterType,
                                                           final int parameterIndex )
  {
    final boolean optional =
      AnnotationsUtil.hasAnnotationOfType( parameter, GeneratorUtil.NULLABLE_ANNOTATION_CLASSNAME );
    final AnnotationMirror annotation =
      AnnotationsUtil.findAnnotationByType( parameter, Constants.DEPENDENCY_CLASSNAME );
    final String qualifier =
      null == annotation ? "" : AnnotationsUtil.getAnnotationValue( annotation, "qualifier" );

    final boolean isDeclaredType = TypeKind.DECLARED == parameterType.getKind();
    final DeclaredType declaredType = isDeclaredType ? (DeclaredType) parameterType : null;
    final boolean isParameterizedType = isDeclaredType && !declaredType.getTypeArguments().isEmpty();
    final DependencyDescriptor.Kind type;
    final TypeMirror dependencyType;
    if ( TypeKind.ARRAY == parameterType.getKind() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME, "be an array type" ),
                                    parameter );
    }
    else if ( null == declaredType )
    {
      type = DependencyDescriptor.Kind.INSTANCE;
      dependencyType = parameterType;
    }
    else if ( !isParameterizedType )
    {
      if ( !( (TypeElement) declaredType.asElement() ).getTypeParameters().isEmpty() )
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "be a raw parameterized type" ),
                                      parameter );
      }
      type = DependencyDescriptor.Kind.INSTANCE;
      dependencyType = parameterType;
    }
    else
    {
      if ( Supplier.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        final TypeMirror typeArgument = declaredType.getTypeArguments().get( 0 );
        if ( TypeKind.WILDCARD == typeArgument.getKind() )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "be a " + Supplier.class.getCanonicalName() +
                                                              " type with a wildcard type parameter" ),
                                        parameter );
        }
        type = DependencyDescriptor.Kind.SUPPLIER;
        dependencyType = typeArgument;
      }
      else if ( Collection.class.getCanonicalName().equals( getClassname( declaredType ) ) )
      {
        final TypeMirror typeArgument = declaredType.getTypeArguments().get( 0 );
        if ( TypeKind.WILDCARD == typeArgument.getKind() )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "be a " + Collection.class.getCanonicalName() +
                                                              " type with a wildcard type parameter" ),
                                        parameter );
        }
        else if ( TypeKind.DECLARED == typeArgument.getKind() &&
                  Supplier.class.getCanonicalName().equals( getClassname( (DeclaredType) typeArgument ) ) )
        {
          final DeclaredType supplierType = (DeclaredType) typeArgument;
          final List<? extends TypeMirror> nestedTypeArguments = supplierType.getTypeArguments();
          if ( nestedTypeArguments.isEmpty() )
          {
            throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                "be a raw parameterized type" ),
                                          parameter );
          }
          else
          {
            final TypeMirror nestedParameterType = nestedTypeArguments.get( 0 );
            if ( TypeKind.WILDCARD == nestedParameterType.getKind() )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                  "be a supplier collection parameter with a wildcard type parameter" ),
                                            parameter );
            }
            else if ( TypeKind.DECLARED == nestedParameterType.getKind() &&
                      isParameterized( (DeclaredType) nestedParameterType ) )
            {
              throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                                  "be a supplier collection parameter that contains a parameterized type" ),
                                            parameter );
            }
            else
            {
              type = DependencyDescriptor.Kind.SUPPLIER_COLLECTION;
              dependencyType = nestedParameterType;
            }
          }
        }
        else if ( TypeKind.DECLARED == typeArgument.getKind() && isParameterized( (DeclaredType) typeArgument ) )
        {
          throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                              "be a collection parameter that contains a parameterized type" ),
                                        parameter );
        }
        else
        {
          type = DependencyDescriptor.Kind.COLLECTION;
          dependencyType = typeArgument;
        }
      }
      else
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                            "be a parameterized type other than the special " +
                                                            "types known by the framework such as " +
                                                            Supplier.class.getCanonicalName() + " and " +
                                                            Collection.class.getCanonicalName() ),
                                      parameter );
      }
    }
    if ( optional && type.isCollection() )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.DEPENDENCY_CLASSNAME,
                                                          "be annotated with @Nullable and be a collection type" ),
                                    parameter );
    }

    final Coordinate coordinate = new Coordinate( qualifier, dependencyType );
    return new DependencyDescriptor( type, coordinate, optional, parameter, parameterIndex );
  }

  private boolean isDefaultTypes( @Nonnull final List<TypeMirror> types )
  {
    return 1 == types.size() && TypeKind.VOID == types.get( 0 ).getKind();
  }

  private void injectableConstructorMustNotBePublic( @Nonnull final ExecutableElement constructor )
  {
    if ( isNotSynthetic( constructor ) &&
         constructor.getModifiers().contains( Modifier.PUBLIC ) &&
         ElementsUtil.isWarningNotSuppressed( constructor, Constants.WARNING_PUBLIC_CONSTRUCTOR ) )
    {
      final String message =
        MemberChecks.toSimpleName( Constants.INJECTABLE_CLASSNAME ) + " target should not have a public " +
        "constructor. The type is instantiated by the injector and should have a package-access constructor. " +
        MemberChecks.suppressedBy( Constants.WARNING_PUBLIC_CONSTRUCTOR );
      processingEnv.getMessager().printMessage( Diagnostic.Kind.WARNING, message, constructor );
    }
  }

  private void injectableConstructorMustNotBeProtected( @Nonnull final ExecutableElement constructor )
  {
    if ( constructor.getModifiers().contains( Modifier.PROTECTED ) &&
         ElementsUtil.isWarningNotSuppressed( constructor, Constants.WARNING_PROTECTED_CONSTRUCTOR ) )
    {
      final String message =
        MemberChecks.toSimpleName( Constants.INJECTABLE_CLASSNAME ) + " target should not have a protected " +
        "constructor. The type is instantiated by the injector and should have a package-access constructor. " +
        MemberChecks.suppressedBy( Constants.WARNING_PROTECTED_CONSTRUCTOR );
      processingEnv.getMessager().printMessage( Diagnostic.Kind.WARNING, message, constructor );
    }
  }

  @Nonnull
  private String getClassname( @Nonnull final DeclaredType declaredType )
  {
    return ( (TypeElement) declaredType.asElement() ).getQualifiedName().toString();
  }

  /**
   * Returns true if the given element is synthetic.
   *
   * @param element to check
   * @return true if and only if the given element is synthetic, false otherwise
   */
  private boolean isNotSynthetic( @Nonnull final Element element )
  {
    final long flags = ( (Symbol) element ).flags();
    return 0 == ( flags & Flags.SYNTHETIC ) && 0 == ( flags & Flags.GENERATEDCONSTR );
  }

  @Nonnull
  private Object loadDescriptor( final String classname, final byte[] data )
    throws IOException
  {
    return _descriptorIO.read( new DataInputStream( new ByteArrayInputStream( data ) ), classname );
  }

  @Nullable
  private byte[] tryLoadDescriptorData( @Nonnull final TypeElement element )
  {
    final byte[] data = tryLoadDescriptorData( StandardLocation.CLASS_PATH, element );
    return null != data ? data : tryLoadDescriptorData( StandardLocation.CLASS_OUTPUT, element );
  }

  @Nullable
  private byte[] tryLoadDescriptorData( @Nonnull final JavaFileManager.Location location,
                                        @Nonnull final TypeElement element )
  {
    final String[] nameParts = extractNameParts( element );
    try
    {
      return IOUtil.readFully( processingEnv.getFiler().getResource( location, nameParts[ 0 ], nameParts[ 1 ] ) );
    }
    catch ( final IOException ignored )
    {
      return null;
    }
    catch ( final RuntimeException e )
    {
      // The javac compiler in Java8 will return a null from the JavaFileManager when it should
      // throw an IOException which later causes a NullPointerException when wrapping the code
      // This ugly hack works around this scenario and just lets the compile continue
      if ( e.getClass().getCanonicalName().equals( "com.sun.tools.javac.util.ClientCodeException" ) &&
           e.getCause() instanceof NullPointerException )
      {
        return null;
      }
      else
      {
        throw e;
      }
    }
  }

  private boolean isParameterized( @Nonnull final DeclaredType nestedParameterType )
  {
    return !( (TypeElement) nestedParameterType.asElement() ).getTypeParameters().isEmpty();
  }
}
