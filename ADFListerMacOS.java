///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.125.0
//DEPS org.graalvm.python:python-language:24.0.0
//DEPS org.graalvm.python:python-resources:24.0.0
//DEPS org.graalvm.python:python-launcher:24.0.0
//DEPS org.graalvm.python:python-embedding:24.0.0
/* 
 * The META-INF directory can be re-generated running jbang in Java mode with and commenting out the FILES directive
 *   JAVA_TOOL_OPTIONS=-agentlib:native-image-agent=config-output-dir=META-INF/native-image/swt/{pid}/
 */
//FILES META-INF/native-image/swt/=META-INF/native-image/swt
//SOURCES ADFLister.java
//PIP amitools

public class ADFListerMacOS {
    public static void main(String[] args) {
        ADFLister.main(args);
    }
}
