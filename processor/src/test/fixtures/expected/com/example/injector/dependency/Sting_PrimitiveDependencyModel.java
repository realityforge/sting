package com.example.injector.dependency;

import javax.annotation.Generated;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Generated("sting.processor.StingProcessor")
final class Sting_PrimitiveDependencyModel implements PrimitiveDependencyModel {
  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment1 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment2 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment3 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment4 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment5 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment6 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment7 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nonnull
  private final PrimitiveDependencyModel_Sting_MyFragment fragment8 = new PrimitiveDependencyModel_Sting_MyFragment();

  @Nullable
  private double node1;

  private boolean node1_allocated;

  @Nullable
  private float node2;

  private boolean node2_allocated;

  @Nullable
  private long node3;

  private boolean node3_allocated;

  @Nullable
  private int node4;

  private boolean node4_allocated;

  @Nullable
  private short node5;

  private boolean node5_allocated;

  @Nullable
  private byte node6;

  private boolean node6_allocated;

  @Nullable
  private char node7;

  private boolean node7_allocated;

  @Nullable
  private boolean node8;

  private boolean node8_allocated;

  Sting_PrimitiveDependencyModel() {
  }

  private double node1() {
    if ( !node1_allocated ) {
      node1_allocated = true;
      node1 = fragment8.$sting$_provideValue8();
    }
    return node1;
  }

  private float node2() {
    if ( !node2_allocated ) {
      node2_allocated = true;
      node2 = fragment8.$sting$_provideValue7();
    }
    return node2;
  }

  private long node3() {
    if ( !node3_allocated ) {
      node3_allocated = true;
      node3 = fragment8.$sting$_provideValue6();
    }
    return node3;
  }

  private int node4() {
    if ( !node4_allocated ) {
      node4_allocated = true;
      node4 = fragment8.$sting$_provideValue5();
    }
    return node4;
  }

  private short node5() {
    if ( !node5_allocated ) {
      node5_allocated = true;
      node5 = fragment8.$sting$_provideValue4();
    }
    return node5;
  }

  private byte node6() {
    if ( !node6_allocated ) {
      node6_allocated = true;
      node6 = fragment8.$sting$_provideValue3();
    }
    return node6;
  }

  private char node7() {
    if ( !node7_allocated ) {
      node7_allocated = true;
      node7 = fragment8.$sting$_provideValue2();
    }
    return node7;
  }

  private boolean node8() {
    if ( !node8_allocated ) {
      node8_allocated = true;
      node8 = fragment8.$sting$_provideValue();
    }
    return node8;
  }

  @Override
  public boolean getValue1() {
    return node8();
  }

  @Override
  public char getValue2() {
    return node7();
  }

  @Override
  public byte getValue3() {
    return node6();
  }

  @Override
  public short getValue4() {
    return node5();
  }

  @Override
  public int getValue5() {
    return node4();
  }

  @Override
  public long getValue6() {
    return node3();
  }

  @Override
  public float getValue7() {
    return node2();
  }

  @Override
  public double getValue8() {
    return node1();
  }
}