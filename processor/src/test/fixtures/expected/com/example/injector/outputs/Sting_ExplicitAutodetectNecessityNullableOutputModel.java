package com.example.injector.outputs;

import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Generated("sting.processor.StingProcessor")
final class Sting_ExplicitAutodetectNecessityNullableOutputModel implements ExplicitAutodetectNecessityNullableOutputModel {
  @Nullable
  private Object node1;

  Sting_ExplicitAutodetectNecessityNullableOutputModel() {
  }

  @Nonnull
  private Object node1() {
    if ( null == node1 ) {
      node1 = Objects.requireNonNull( ExplicitAutodetectNecessityNullableOutputModel_Sting_MyModel.create() );
    }
    assert null != node1;
    return node1;
  }

  @Override
  @Nullable
  public ExplicitAutodetectNecessityNullableOutputModel.MyModel getMyModel() {
    return (ExplicitAutodetectNecessityNullableOutputModel.MyModel) node1();
  }
}
