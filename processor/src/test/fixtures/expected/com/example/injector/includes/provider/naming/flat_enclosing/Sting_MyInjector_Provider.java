package com.example.injector.includes.provider.naming.flat_enclosing;

import javax.annotation.Generated;
import javax.annotation.Nonnull;
import sting.Fragment;

@Generated("sting.processor.StingProcessor")
@Fragment
public interface Sting_MyInjector_Provider {
  @Nonnull
  default MyInjector provide() {
    return new Sting_MyInjector();
  }

  default MyModel getMyModel(final MyInjector injector) {
    return injector.getMyModel();
  }

  default Outer.Middle.Leaf.MyModel2 getMyModel2(final MyInjector injector) {
    return injector.getMyModel2();
  }
}