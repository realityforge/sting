package com.example.injector.includes;

import java.util.Objects;
import javax.annotation.Generated;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Generated("sting.processor.StingProcessor")
final class Sting_RecursiveIncludesModel implements RecursiveIncludesModel {
  @Nonnull
  private final RecursiveIncludesModel_Sting_MyFragment1 fragment1 = new RecursiveIncludesModel_Sting_MyFragment1();

  @Nonnull
  private final RecursiveIncludesModel_Sting_MyFragment2 fragment2 = new RecursiveIncludesModel_Sting_MyFragment2();

  @Nonnull
  private final RecursiveIncludesModel_Sting_MyFragment3 fragment3 = new RecursiveIncludesModel_Sting_MyFragment3();

  @Nullable
  private Object node1;

  @Nullable
  private Object node2;

  @Nullable
  private Object node3;

  @Nullable
  private Runnable node4;

  @Nullable
  private Runnable node5;

  @Nullable
  private Runnable node6;

  Sting_RecursiveIncludesModel() {
  }

  @Nonnull
  private Object node1() {
    if ( null == node1 ) {
      node1 = Objects.requireNonNull( RecursiveIncludesModel_Sting_MyModel3.create() );
    }
    assert null != node1;
    return node1;
  }

  @Nonnull
  private Object node2() {
    if ( null == node2 ) {
      node2 = Objects.requireNonNull( RecursiveIncludesModel_Sting_MyModel2.create() );
    }
    assert null != node2;
    return node2;
  }

  @Nonnull
  private Object node3() {
    if ( null == node3 ) {
      node3 = Objects.requireNonNull( RecursiveIncludesModel_Sting_MyModel1.create() );
    }
    assert null != node3;
    return node3;
  }

  @Nonnull
  private Runnable node4() {
    if ( null == node4 ) {
      node4 = Objects.requireNonNull( fragment3.$sting$_provideRunnable() );
    }
    assert null != node4;
    return node4;
  }

  @Nonnull
  private Runnable node5() {
    if ( null == node5 ) {
      node5 = Objects.requireNonNull( fragment2.$sting$_provideRunnable() );
    }
    assert null != node5;
    return node5;
  }

  @Nonnull
  private Runnable node6() {
    if ( null == node6 ) {
      node6 = Objects.requireNonNull( fragment1.$sting$_provideRunnable() );
    }
    assert null != node6;
    return node6;
  }

  @Override
  public Runnable getRunnable1() {
    return node6();
  }

  @Override
  public Runnable getRunnable2() {
    return node5();
  }

  @Override
  public Runnable getRunnable3() {
    return node4();
  }

  @Override
  public RecursiveIncludesModel.MyModel1 getMyModel1() {
    return (RecursiveIncludesModel.MyModel1) node3();
  }

  @Override
  public RecursiveIncludesModel.MyModel2 getMyModel2() {
    return (RecursiveIncludesModel.MyModel2) node2();
  }

  @Override
  public RecursiveIncludesModel.MyModel3 getMyModel3() {
    return (RecursiveIncludesModel.MyModel3) node1();
  }
}
