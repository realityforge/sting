package com.example.injector.outputs;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javaemul.internal.annotations.DoNotInline;
import javax.annotation.Generated;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Generated("sting.processor.StingProcessor")
final class Sting_CollectionOutputModel implements CollectionOutputModel {
  @Nullable
  private Object node1;

  Sting_CollectionOutputModel() {
  }

  @Nonnull
  @DoNotInline
  private Object node1() {
    if ( null == node1 ) {
      node1 = Objects.requireNonNull( CollectionOutputModel_Sting_MyModel.create() );
    }
    assert null != node1;
    return node1;
  }

  @Override
  public Collection<CollectionOutputModel.MyModel> getMyModel() {
    return Collections.singletonList( (CollectionOutputModel.MyModel) node1() );
  }
}
