package com.example.injector.outputs;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import sting.Injectable;
import sting.Injector;
import sting.Named;
import sting.Service;

@Injector
interface ComplexOutputModel
{
  MyModel1 getMyModel1();

  Supplier<MyModel2> getMyModel2Supplier();

  @Named( "foo" )
  Supplier<MyModel3> getMyModel3Supplier();

  @Nullable
  MyModel4 getMyModel4();

  @Injectable
  class MyModel1
  {
  }

  @Injectable
  class MyModel2
  {
  }

  @Injectable( services = @Service( qualifier = "foo" ) )
  class MyModel3
  {
  }

  // Not @Injectable and thus would need to be provided if used
  // but not used and nullable Dependency
  class MyModel4
  {
  }
}
