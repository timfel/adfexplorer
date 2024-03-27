///usr/bin/env jbang "$0" "$@" ; exit $?

/* 
 * The META-INF directory can be re-generated running jbang in Java mode with and commenting out the FILES directive
 *   JAVA_TOOL_OPTIONS=-agentlib:native-image-agent=config-output-dir=META-INF/native-image/guitrace{pid}/
 */
//FILES META-INF/native-image=META-INF/native-image

/* 
 * Adapt the SWT dependency depending on the platform
 */
//DEPS org.eclipse.platform:org.eclipse.swt.gtk.linux.x86_64:3.125.0
////DEPS org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.125.0
////DEPS org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.125.0
////DEPS org.eclipse.platform:org.eclipse.swt.cocoa.macosx.aarch64:3.125.0

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

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.utils.VirtualFileSystem;

public class ADFLister {
    private static Context context;
    private static Value BlkDevFactory;
    private static Value ADFSVolume;
    private static Value volume;

    public static void main(String[] args) {
        context = VirtualGraalPyContext.getContext();
        BlkDevFactory = context.eval("python", "from amitools.fs.blkdev.BlkDevFactory import BlkDevFactory; BlkDevFactory");
        ADFSVolume = context.eval("python", "from amitools.fs.ADFSVolume import ADFSVolume; ADFSVolume");

        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("ADF Explorer");
        shell.setLayout(new FillLayout());

        // Create a multi-line text widget with read-only style
        Text textArea = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        textArea.setText("");

        for (String arg : args) {
            acceptFile(textArea, arg);
        }

        int operations = DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK;
        DropTarget target = new DropTarget(textArea, operations);

        final FileTransfer fileTransfer = FileTransfer.getInstance();
        Transfer[] types = new Transfer[] { fileTransfer };
        target.setTransfer(types);

        target.addDropListener(new DropTargetAdapter() {
            public void drop(DropTargetEvent event) {
                if (fileTransfer.isSupportedType(event.currentDataType)) {
                    String[] files = (String[]) event.data;
                    for (String file : files) {
                        acceptFile(textArea, file);
                    }
                }
            }
        });

        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();

    }

    static void acceptFile(Text textarea, String path) {
        textarea.append(path + "\n");
        var volume = openVolume(path);
        var contents = listVolume(volume);
        for (var content : contents) {
            textarea.append("↳ " + content + "\n");
        }
    }

    static Value openVolume(String filename) {
        volume = ADFSVolume.newInstance(BlkDevFactory.newInstance().invokeMember("open", filename));
        volume.invokeMember("open");
        return volume;
    }

    static List<String> listEntry(Value e, int indent) {
        var result = new ArrayList<String>();
        result.add(e.invokeMember("get_list_str", indent).asString());
        if (e.hasMember("ensure_entries")) {
            e.invokeMember("ensure_entries");
            var es = e.invokeMember("get_entries_sorted_by_name").getIterator();
            while (es.hasIteratorNextElement()) {
                result.addAll(listEntry(es.getIteratorNextElement(), indent + 1));
            }
        }
        return result;
    }

    static List<String> listVolume(Value vol) {
        var dir = vol.getMember("root_dir");
        var lines = listEntry(dir, 0);
        var node = vol.invokeMember("get_root_dir");
        var info = node.invokeMember("get_info", true);
        var infoIter = info.getIterator();
        while (infoIter.hasIteratorNextElement()) {
            lines.add(infoIter.getIteratorNextElement().asString());
        }
        return lines;
    }
}

final class VirtualGraalPyContext {
    private static final String VENV_PREFIX = "/vfs/venv";
    private static final String HOME_PREFIX = "/vfs/home";

    public static Context getContext() {
        VirtualFileSystem vfs = VirtualFileSystem.create();
        var builder = Context.newBuilder()
            // set true to allow experimental options
            .allowExperimentalOptions(false)
            // deny all privileges unless configured below
            .allowAllAccess(false)
            // allow access to the virtual and the host filesystem, as well as sockets
            .allowIO(IOAccess.newBuilder()
                            .allowHostSocketAccess(true)
                            .fileSystem(vfs)
                            .build())
            // allow creating python threads
            .allowCreateThread(true)
            // allow running Python native extensions
            .allowNativeAccess(true)
            // allow exporting Python values to polyglot bindings and accessing Java from Python
            .allowPolyglotAccess(PolyglotAccess.ALL)
            // choose the backend for the POSIX module
            .option("python.PosixModuleBackend", "java")
            // equivalent to the Python -B flag
            .option("python.DontWriteBytecodeFlag", "true")
            // equivalent to the Python -v flag
            .option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false")
            // log level
            .option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE")
            // equivalent to setting the PYTHONWARNINGS environment variable
            .option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS"))
            // print Python exceptions directly
            .option("python.AlwaysRunExcepthook", "true")
            // Force to automatically import site.py module, to make Python packages available
            .option("python.ForceImportSite", "true")
            // The sys.executable path, a virtual path that is used by the interpreter to discover packages
            .option("python.Executable", vfs.resourcePathToPlatformPath(VENV_PREFIX) + (VirtualFileSystem.isWindows() ? "\\Scripts\\python.exe" : "/bin/python"))
            // Do not warn if running without JIT. This can be desirable for short running scripts
            // to reduce memory footprint.
            .option("engine.WarnInterpreterOnly", "false");
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            // Set the python home to be read from the embedded resources
            builder.option("python.PythonHome", vfs.resourcePathToPlatformPath(HOME_PREFIX));
        }
        return builder.build();
    }
}
