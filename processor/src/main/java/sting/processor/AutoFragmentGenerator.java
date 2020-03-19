package sting.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.realityforge.proton.GeneratorUtil;
import org.realityforge.proton.SuppressWarningsUtil;

final class AutoFragmentGenerator
{
  private AutoFragmentGenerator()
  {
  }

  @Nonnull
  static TypeSpec buildType( @Nonnull final ProcessingEnvironment processingEnv,
                             @Nonnull final AutoFragmentDescriptor autoFragment )
  {
    final TypeElement element = autoFragment.getElement();
    final TypeSpec.Builder builder =
      TypeSpec
        .interfaceBuilder( GeneratorUtil.getGeneratedSimpleClassName( element, "Sting_", "_Fragment" ) )
        .addModifiers( Modifier.PUBLIC );
    GeneratorUtil.addOriginatingTypes( element, builder );
    GeneratorUtil.addGeneratedAnnotation( processingEnv, builder, StingProcessor.class.getName() );

    GeneratorUtil.copyWhitelistedAnnotations( element, builder );
    SuppressWarningsUtil.addSuppressWarningsIfRequired( processingEnv,
                                                        builder,
                                                        Collections.emptyList(),
                                                        Collections.singletonList( element.asType() ) );

    final AnnotationSpec.Builder annotation =
      AnnotationSpec.builder( ClassName.get( "sting", "Fragment" ) );
    final List<TypeElement> contributors =
      autoFragment.getContributors()
        .stream()
        .sorted( Comparator.comparing( e -> e.getQualifiedName().toString() ) )
        .collect( Collectors.toList() );
    for ( final TypeElement contributor : contributors )
    {
      annotation.addMember( "includes", "$T.class", contributor.asType() );
    }
    builder.addAnnotation( annotation.build() );

    return builder.build();
  }
}
