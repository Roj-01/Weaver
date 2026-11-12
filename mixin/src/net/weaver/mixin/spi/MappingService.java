package net.weaver.mixin.spi;

/**
 * Provisional binding of the {@code MappingService} contract (CONTRACTS.md section 5) to the
 * mixin refmap path. Track 04 owns the production implementation; this module depends only on
 * the interface so no mapping table is hardcoded here.
 */
public interface MappingService {

    String runtimeNamespace();

    byte[] remapClass(byte[] input, String from, String to);

    java.nio.file.Path remapJar(java.nio.file.Path input, String from, String to, java.nio.file.Path output);

    String mapClass(String name, String from, String to);

    String mapMethod(String owner, String name, String desc, String from, String to);

    String mapField(String owner, String name, String desc, String from, String to);

    String mapDescriptor(String desc, String from, String to);
}
