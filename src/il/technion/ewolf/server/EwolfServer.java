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
import il.technion.ewolf.server.ServerResources.EwolfConfigurations;
import il.technion.ewolf.server.handlers.JarResourceHandler;
import il.technion.ewolf.server.handlers.JsonHandler;
import il.technion.ewolf.server.handlers.SFShandler;
import il.technion.ewolf.server.handlers.SFSUploadHandler;
import il.technion.ewolf.server.jsonDataHandlers.AddWolfpackMemberHandler;
import il.technion.ewolf.server.jsonDataHandlers.CreateWolfpackHandler;
import il.technion.ewolf.server.jsonDataHandlers.InboxFetcher;
import il.technion.ewolf.server.jsonDataHandlers.NewsFeedFetcher;
import il.technion.ewolf.server.jsonDataHandlers.PostToNewsFeedHandler;
import il.technion.ewolf.server.jsonDataHandlers.ProfileFetcher;
import il.technion.ewolf.server.jsonDataHandlers.SendMessageHandler;
import il.technion.ewolf.server.jsonDataHandlers.WolfpackMembersFetcher;
import il.technion.ewolf.server.jsonDataHandlers.WolfpacksFetcher;
import il.technion.ewolf.socialfs.SocialFSCreatorModule;
import il.technion.ewolf.socialfs.SocialFSModule;
import il.technion.ewolf.stash.StashModule;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class EwolfServer {
	
	private static final String EWOLF_CONFIG = "/ewolf.config.properties";
	
	EwolfConfigurations configurations;
	Injector serverInjector;
	HttpConnector serverConnector;
	Injector ewolfInjector;

	private JsonHandler jsonHandler;
	
	public EwolfServer(EwolfConfigurations configurations) {
		if(configurations == null) {
			throw new IllegalArgumentException("Configuration file is missing.");
		}

		this.configurations = configurations;
	}

	public static void main(String[] args) throws Exception {		
		EwolfConfigurations myServerConfigurations = 
				ServerResources.getConfigurations(EWOLF_CONFIG);
		
		EwolfServer server = new EwolfServer(myServerConfigurations);
		server.initEwolf();
	}

	private Injector createDefaultInjector() {
		String port = String.valueOf(configurations.serverPort);

		return Guice.createInjector(
				new HttpConnectorModule()
					.setProperty("httpconnector.net.port", port),
				new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "20")
					.setProperty("openkad.bucket.kbuckets.maxsize", "20")
					.setProperty("openkad.seed", port)
					.setProperty("openkad.net.udp.port", port));
	}

	public void initEwolf() throws IOException, Exception {

		this.serverInjector = createDefaultInjector();

		serverConnector = serverInjector.getInstance(HttpConnector.class);
		serverConnector.bind();
		registerConnectorHandlers();
		serverConnector.start();

		if (configurations.username != null) {
			this.ewolfInjector = createInjector();

			KeybasedRouting kbr = ewolfInjector.getInstance(KeybasedRouting.class);
			kbr.create();

			// bind the chunkeeper
			ChunKeeper chnukeeper = ewolfInjector.getInstance(ChunKeeper.class);
			chnukeeper.bind();

			HttpConnector ewolfConnector = ewolfInjector.getInstance(HttpConnector.class);
			ewolfConnector.bind();
			ewolfConnector.start();

			EwolfAccountCreator accountCreator =
					ewolfInjector.getInstance(EwolfAccountCreator.class);
			accountCreator.create();

			//FIXME port for testing
			kbr.join(configurations.kbrURIs);

			new Thread(ewolfInjector.getInstance(PokeMessagesAcceptor.class),
					"PokeMessagesAcceptorThread").start();
			addHandlers(jsonHandler);
		} else {
			//TODO
		}

		System.out.println("Server started.");
	}
	
	private void registerConnectorHandlers() {
		jsonHandler = new JsonHandler();
		serverConnector.register("/json*", jsonHandler);
//		serverConnector.register("/sfsupload*", serverInjector.getInstance(SFSUploadHandler.class));
//		serverConnector.register("/sfs*", serverInjector.getInstance(SFShandler.class));

		serverConnector.register("*", new JarResourceHandler());
	}
	
	private JsonHandler addHandlers(JsonHandler jsonHandler) {
		return jsonHandler
		.addHandler("inbox", ewolfInjector.getInstance(InboxFetcher.class))
		.addHandler("wolfpacks", ewolfInjector.getInstance(WolfpacksFetcher.class))
		.addHandler("profile", ewolfInjector.getInstance(ProfileFetcher.class))
		.addHandler("wolfpackMembers", ewolfInjector.getInstance(WolfpackMembersFetcher.class))
		.addHandler("newsFeed", ewolfInjector.getInstance(NewsFeedFetcher.class))
		.addHandler("createWolfpack", ewolfInjector.getInstance(CreateWolfpackHandler.class))
		.addHandler("addWolfpackMember", ewolfInjector.getInstance(AddWolfpackMemberHandler.class))
		.addHandler("post", ewolfInjector.getInstance(PostToNewsFeedHandler.class))
		.addHandler("sendMessage", ewolfInjector.getInstance(SendMessageHandler.class));
	}

	private Injector createInjector() {
		String port = String.valueOf(configurations.ewolfPort);
		
		return Guice.createInjector(

				new KadNetModule()
					.setProperty("openkad.keyfactory.keysize", "20")
					.setProperty("openkad.bucket.kbuckets.maxsize", "20")
					.setProperty("openkad.seed", port)
					.setProperty("openkad.net.udp.port", port),
					
				new HttpConnectorModule()
					.setProperty("httpconnector.net.port", port),

				new SimpleDHTModule()
					//TODO temporary property - replicating bug workaround
					.setProperty("dht.storage.checkInterval", ""+TimeUnit.HOURS.toMillis(1)),
					
				new ChunKeeperModule(),
				
				new StashModule(),
				
				new SocialFSCreatorModule()
					.setProperty("socialfs.user.username", 
							configurations.username)
					.setProperty("socialfs.user.password", 
							configurations.password)
					.setProperty("socialfs.user.name", 
							configurations.name),

				new SocialFSModule(),
				
				new EwolfAccountCreatorModule(),

				new EwolfModule()
		);
	}
}
