package sting.processor;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.json.stream.JsonGenerator;
import javax.lang.model.type.TypeMirror;

/**
 * The mechanism for identifying a service.
 */
final class Coordinate
{
  /**
   * An opaque string used to restrict the services that match a coordinate.
   */
  @Nonnull
  private final String _qualifier;
  /**
   * The java type of the service.
   */
  @Nonnull
  private final TypeMirror _type;

  Coordinate( @Nonnull final String qualifier, @Nonnull final TypeMirror type )
  {
    _qualifier = Objects.requireNonNull( qualifier );
    _type = Objects.requireNonNull( type );
  }

  @Nonnull
  String getQualifier()
  {
    return _qualifier;
  }

  @Nonnull
  TypeMirror getType()
  {
    return _type;
  }

  void write( @Nonnull final JsonGenerator g )
  {
    if ( !_qualifier.isEmpty() )
    {
      g.write( "qualifier", _qualifier );
    }
    g.write( "type", _type.toString() );
  }

  @Override
  public String toString()
  {
    return "[" + _type + ( _qualifier.isEmpty() ? "" : ";qualifier='" + _qualifier + "'" ) + "]";
  }

  @Override
  public boolean equals( final Object o )
  {
    assert o instanceof Coordinate;
    final Coordinate coordinate = (Coordinate) o;
    return _qualifier.equals( coordinate._qualifier ) && _type.toString().equals( coordinate._type.toString() );
  }

  @Override
  public int hashCode()
  {
    return Objects.hash( _qualifier, _type.toString() );
  }
}
