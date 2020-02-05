package com.example.fragment.types;

import java.util.EventListener;
import java.util.Properties;
import sting.Fragment;
import sting.Provides;
import sting.Service;

@Fragment
public interface BasicTypesModel
{
  class MyModel
    extends Properties
    implements Runnable, EventListener, AutoCloseable
  {
    private static final long serialVersionUID = -3456430195886129035L;

    @Override
    public void run()
    {
    }

    @Override
    public void close()
    {
    }
  }

  // Does not expose BasicTypesModel or AutoCloseable nor Hashtable and related
  @Provides( services = { @Service( type = Runnable.class ),
                          @Service( type = EventListener.class ),
                          @Service( type = Object.class ),
                          @Service( type = Properties.class ) } )
  default MyModel provideMyModel()
  {
    return null;
  }
}
