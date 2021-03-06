package il.technion.ewolf.server;

import il.technion.ewolf.chunkeeper.ChunKeeper;
import il.technion.ewolf.chunkeeper.ChunKeeperModule;
import il.technion.ewolf.dht.SimpleDHTModule;
import il.technion.ewolf.ewolf.EwolfAccountCreator;
import il.technion.ewolf.ewolf.EwolfAccountCreatorModule;
import il.technion.ewolf.ewolf.EwolfModule;
import il.technion.ewolf.http.HttpConnector;
import il.technion.ewolf.http.HttpConnectorModule;
import il.technion.ewolf.kbr.KeybasedRouting;
import il.technion.ewolf.kbr.openkad.KadNetModule;
import il.technion.ewolf.socialfs.SocialFSCreatorModule;
import il.technion.ewolf.socialfs.SocialFSModule;
import il.technion.ewolf.stash.StashModule;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class DummyEwolfNet {
	private static final int BASE_PORT = 10100;

	private static final String EWOLF_CONFIG_1 = "/ewolf.config.properties";

	private static final String EWOLF_CONFIG_2 = "/ewolf2.config.properties";

	public static void main(String[] args) throws Exception {
		List<Injector> injectors = new LinkedList<Injector>();

		for (int i = 0; i < 2; ++i) {
			Injector injector = Guice.createInjector(
					new KadNetModule()
							.setProperty("openkad.keyfactory.keysize", "20")
							.setProperty("openkad.bucket.kbuckets.maxsize",
									"20")
							.setProperty("openkad.net.udp.port",
									"" + (BASE_PORT + i)),

					new HttpConnectorModule().setProperty(
							"httpconnector.net.port", "" + (BASE_PORT + i)),

					new SimpleDHTModule(),

					new ChunKeeperModule(),

					new StashModule(),

					new SocialFSCreatorModule().setProperty(
							"socialfs.user.username", "user_" + i).setProperty(
							"socialfs.user.password", "1234"),

					new SocialFSModule(),

					new EwolfAccountCreatorModule(),

					new EwolfModule());
			injectors.add(injector);
		}

		for (Injector injector : injectors) {

			// start the Keybased routing
			KeybasedRouting kbr = injector.getInstance(KeybasedRouting.class);
			kbr.create();

			// bind the http connector
			HttpConnector connector = injector.getInstance(HttpConnector.class);
			connector.bind();
			connector.start();

			// bind the chunkeeper
			ChunKeeper chnukeeper = injector.getInstance(ChunKeeper.class);
			chnukeeper.bind();
		}

		for (int i = 1; i < injectors.size(); ++i) {
			int port = BASE_PORT + i - 1;
			System.out.println(i + " ==> " + (i - 1));
			KeybasedRouting kbr = injectors.get(i).
					getInstance(KeybasedRouting.class);
			kbr.join(Arrays.asList(new URI("openkad.udp://127.0.0.1:"
					+ port + "/")));
		}

		for (Injector injector : injectors) {
			System.out.println("creating...");
			EwolfAccountCreator accountCreator = injector
					.getInstance(EwolfAccountCreator.class);
			accountCreator.create();
			System.out.println("done\n");
		}

		EwolfServer server1 = new EwolfServer(EWOLF_CONFIG_1);
		server1.initEwolf();

		EwolfServer server2 = new EwolfServer(EWOLF_CONFIG_2);
		server2.initEwolf();
	}
}
