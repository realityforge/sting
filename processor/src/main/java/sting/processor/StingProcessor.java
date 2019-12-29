package sting.processor;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.realityforge.proton.AbstractStandardProcessor;
import org.realityforge.proton.ElementsUtil;
import org.realityforge.proton.MemberChecks;
import org.realityforge.proton.ProcessorException;

/**
 * Annotation processor that analyzes sting annotated source and generates dependency injection container.
 */
@SupportedAnnotationTypes( Constants.INJECTABLE_CLASSNAME )
@SupportedSourceVersion( SourceVersion.RELEASE_8 )
@SupportedOptions( { "sting.defer.unresolved", "sting.defer.errors" } )
public final class StingProcessor
  extends AbstractStandardProcessor
{
  @Nonnull
  @Override
  protected String getIssueTrackerURL()
  {
    return "https://github.com/realityforge/sting/issues";
  }

  @Nonnull
  @Override
  protected String getOptionPrefix()
  {
    return "sting";
  }

  @SuppressWarnings( "unchecked" )
  @Nonnull
  @Override
  protected Collection<TypeElement> getTypeElementsToProcess( @Nonnull final RoundEnvironment env )
  {
    final TypeElement annotation = processingEnv.getElementUtils().getTypeElement( Constants.INJECTABLE_CLASSNAME );
    return (Collection<TypeElement>) env.getElementsAnnotatedWith( annotation );
  }

  protected void process( @Nonnull final TypeElement element )
    throws IOException, ProcessorException
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
    else if ( isEnclosedInNonStaticClass( element ) )
    {
      throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTABLE_CLASSNAME,
                                                          "be a non-static nested class" ),
                                    element );
    }
    final List<ExecutableElement> constructors = ElementsUtil.getConstructors( element );
    if ( !constructors.isEmpty() )
    {
      final ExecutableElement constructor = constructors.get( 0 );
      if ( constructors.size() > 1 )
      {
        throw new ProcessorException( MemberChecks.mustNot( Constants.INJECTABLE_CLASSNAME,
                                                            "have multiple constructors" ),
                                      element );
      }
      constructorMustNotBeProtected( constructor );
      constructorMustNotBePublic( constructor );
    }
  }

  private boolean isEnclosedInNonStaticClass( @Nonnull final TypeElement element )
  {
    final Element parent = element.getEnclosingElement();
    if ( parent instanceof TypeElement )
    {
      if ( element.getModifiers().contains( Modifier.STATIC ) )
      {
        return isEnclosedInNonStaticClass( (TypeElement) parent );
      }
      else
      {
        return true;
      }
    }
    else
    {
      return false;
    }
  }

  private void constructorMustNotBePublic( @Nonnull final ExecutableElement constructor )
  {
    if ( constructor.getModifiers().contains( Modifier.PUBLIC ) &&
         ElementsUtil.isWarningNotSuppressed( constructor, Constants.WARNING_PUBLIC_CONSTRUCTOR ) )
    {
      final String message =
        MemberChecks.toSimpleName( Constants.INJECTABLE_CLASSNAME ) + " target should not have a public " +
        "constructor. The type is instantiated by the injector and should have a package-access constructor. " +
        MemberChecks.suppressedBy( Constants.WARNING_PUBLIC_CONSTRUCTOR );
      processingEnv.getMessager().printMessage( Diagnostic.Kind.WARNING, message, constructor );
    }
  }

  private void constructorMustNotBeProtected( @Nonnull final ExecutableElement constructor )
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
}
