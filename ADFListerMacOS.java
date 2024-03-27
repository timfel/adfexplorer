///usr/bin/env jbang "$0" "$@" ; exit $?

/* 
 * The META-INF directory can be re-generated running jbang in Java mode with and commenting out the FILES directive
 *   JAVA_TOOL_OPTIONS=-agentlib:native-image-agent=config-output-dir=META-INF/native-image/macosx{pid}/
 */
//FILES META-INF/native-image/macosx=META-INF/native-image/macosx
//DEPS org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.125.0
//SOURCES ADFLister.java

/*
 * To reduce the size of the resulting binary, these options can be used when creating
 * a native image (see https://www.graalvm.org/latest/reference-manual/python/native-applications/)
 *   -Dpython.WithoutSSL=true
 *   -Dpython.WithoutDigest=true
 *   -Dpython.WithoutPlatformAccess=true
 *   -Dpython.WithoutCompressionLibraries=true
 *   -Dpython.WithoutNativePosix=true
 *   -Dpython.WithoutJavaInet=true
 *   -Dimage-build-time.PreinitializeContexts=
 */
//DEPS org.graalvm.python:python-language:24.0.0
//DEPS org.graalvm.python:python-resources:24.0.0
//DEPS org.graalvm.python:python-launcher:24.0.0
//DEPS org.graalvm.python:python-embedding:24.0.0
//PIP amitools

public class ADFListerMacOS {
    public static void main(String[] args) {
        ADFLister.main(args);
    }
}
