import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Pushes the classes compiled since the running dev client started into that client, so a code change shows up without a restart.
 * Run with {@code gradlew hotswap} (it compiles first). The client is started with a debug port (see build.gradle) and this attaches to it.
 * Only method bodies can be swapped: adding or removing a method, a field or a class is refused by the JVM and needs a restart.
 * Changed textures, models and lang files need no code swap: press F3+T in the game.
 */
public class HotSwap {
    public static void main(String[] args) throws Exception {
        Path classes = Path.of("build/classes/java/main"), stamp = Path.of("build/hotswap.stamp");
        long since = Files.exists(stamp) ? Files.getLastModifiedTime(stamp).toMillis() : 0L;
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, Connector.Argument> a = connector.defaultArguments();
        a.get("hostname").setValue("127.0.0.1");
        a.get("port").setValue("5005");
        VirtualMachine vm;
        try {
            vm = connector.attach(a);
        } catch (Exception e) {
            System.out.println("HOTSWAP: no running client to update (" + e.getMessage() + "): it will use the new code when it starts");
            return;
        }
        Map<ReferenceType, byte[]> swap = new HashMap<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                if (Files.getLastModifiedTime(f).toMillis() <= since) continue;
                String name = classes.relativize(f).toString().replace('\\', '/').replaceAll("\\.class$", "").replace('/', '.');
                List<ReferenceType> loaded = vm.classesByName(name);
                if (loaded.isEmpty()) continue; // not loaded yet: it will load from disk when it is first used
                swap.put(loaded.get(0), Files.readAllBytes(f));
            }
        }
        if (swap.isEmpty()) {
            System.out.println("HOTSWAP: nothing to update");
        } else {
            try {
                vm.redefineClasses(swap);
                System.out.println("HOTSWAP: updated " + swap.size() + " classes in the running client");
            } catch (UnsupportedOperationException | NoClassDefFoundError e) {
                System.out.println("HOTSWAP: the running client cannot take this change (" + e.getMessage() + "): a method, field or class was added or removed, so restart the client");
                System.exit(2);
            } catch (Exception e) {
                System.out.println("HOTSWAP: failed (" + e + "): restart the client");
                System.exit(2);
            }
        }
        Files.write(stamp, new byte[0]);
        vm.dispose();
    }
}
