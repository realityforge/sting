# Change Log

### [v0.04](https://github.com/sting-ioc/sting/tree/v0.04) (2020-02-19) · [Full Changelog](https://github.com/sting-ioc/sting/compare/v0.03...v0.04)

Changes in this release:

* Improve the javadocs organization.
* Build and measure code size of downstream sample projects to help identify track code size changes as the library evolves.
* Generate a suppressable warning when an `@Injectable` annotated type is also annotated with an annotation that is annotated by `@javax.inject.Scope` that is used in other injection frameworks like CDI, Dagger, Guice, GIN etc. This generates a warning as it is unusual that an application uses multiple injection frameworks and the presence of a scoped annotation is more likely the result of a misunderstanding or incomplete conversion from a prior injection framework.
* Generate an error if a `@Fragment` annotated type or a provider method enclosed by the fragment has a jsr330 `@javax.inject.Scope` annotated annotation present.
* Generate an error if an `@Injector` annotated type or an output method enclosed by the injector has a jsr330 `@javax.inject.Scope` annotated annotation present.

### [v0.03](https://github.com/sting-ioc/sting/tree/v0.03) (2020-02-18) · [Full Changelog](https://github.com/sting-ioc/sting/compare/v0.02...v0.03)

Changes in this release:

* Improve the POM generated for both the `sting-core` and `sting-processor` modules.

### [v0.02](https://github.com/sting-ioc/sting/tree/v0.02) (2020-02-17) · [Full Changelog](https://github.com/sting-ioc/sting/compare/v0.01...v0.02)

Changes in this release:

* Upgrade the `org.realityforge.proton` artifacts to version `0.40`.
* Stop generating errors if types are annotated with `@Named`, `@Typed` or `@Eager` and they have a provider annotation.
* Correct bug where a sting provider annotated type was constructing the classname of the target provider relative to the package of the injector rather the package of the declaring type.
* Change the return type of the factory method used to provide `@Injectable` values to return a `java.lang.Object` rather than a package-access type. This is to simplify integration with some downstream tools that assume public methods work with public types and to align with the pattern used for passing package-access dependencies into the factory method.
* Fix broken code that was generated when a binding is not public but the services it provides are public. The code was previously omitting some required casts.

### [v0.01](https://github.com/sting-ioc/sting/tree/v0.01) (2020-02-13) · [Full Changelog](https://github.com/sting-ioc/sting/compare/9e796d0e5c44bee98107f3e65bd394d41bbe07c7...v0.01)

Changes in this release:

 ‎🎉	Initial super-alpha release ‎🎉.
