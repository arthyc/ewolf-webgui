package il.technion.ewolf.server.cache;

import il.technion.ewolf.ewolf.SocialNetwork;
import il.technion.ewolf.ewolf.WolfPack;
import il.technion.ewolf.ewolf.WolfPackLeader;
import il.technion.ewolf.exceptions.WallNotFound;
import il.technion.ewolf.msg.SocialMail;
import il.technion.ewolf.msg.SocialMessage;
import il.technion.ewolf.posts.Post;
import il.technion.ewolf.socialfs.Profile;
import il.technion.ewolf.socialfs.SocialFS;
import il.technion.ewolf.socialfs.UserID;
import il.technion.ewolf.socialfs.UserIDFactory;
import il.technion.ewolf.socialfs.exception.ProfileNotFoundException;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class CacheModule extends AbstractModule {
	private final Properties properties;

	private Properties getDefaultProperties() {
		Properties defaultProps = new Properties();

		defaultProps.setProperty("server.cache.newsfeed.intervalSec", "30");
		defaultProps.setProperty("server.cache.wolfpacks.intervalSec", "30");
		defaultProps.setProperty("server.cache.inbox.intervalSec", "30");
		defaultProps.setProperty("server.cache.wolfpackMembers.intervalSec", "30");

		return defaultProps;
	}

	public CacheModule() {
		this(new Properties());
	}

	public CacheModule(Properties properties) {
		this.properties = getDefaultProperties();
		this.properties.putAll(properties);
	}

	@Override
	protected void configure() {
		Names.bindProperties(binder(), properties);
	}

	@Provides
	@Singleton
	ICacheWithParameter<Profile, String> provideProfilesCache(
			final SocialFS socialFS, final UserIDFactory userIDFactory) {
		return new ICacheWithParameter<Profile, String>() {
			Map<String,Profile> profilesCache = new ConcurrentHashMap<String, Profile>();

			{
				// add self profile to cache mapped to "-1"
				profilesCache.put("-1", socialFS.getCredentials().getProfile());
			}

			/**
			 * @param	strUid	user ID or "-1" for self ID
			 * @return	profile corresponding to userID or null if not found
			 */
			@Override
			public Profile get(String strUid) {
				if (!profilesCache.containsKey(strUid)) {
					try {
						UserID uid = userIDFactory.getFromBase64(strUid);
						Profile profile = socialFS.findProfile(uid);
						profilesCache.put(strUid, profile);
					} catch (ProfileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				return profilesCache.get(strUid);
			}
		};
	}

	@Provides
	@Singleton
	ICache<List<SocialMessage>> provideInboxCache(
			@Named("server.cache.inbox.intervalSec") int cachedTimeSec,
			final SocialMail smail) {
		return new SimpleCache<List<SocialMessage>>(
				new ICache<List<SocialMessage>>() {
					@Override
					public List<SocialMessage> get() {
						return smail.readInbox();
					}

					@Override
					public void update() {
						get();
					}
				}, cachedTimeSec);
	}


	@Provides
	@Singleton
	ICache<Map<String, WolfPack>> provideWolfpacksCache(
			@Named("server.cache.wolfpacks.intervalSec") int cachedTimeSec,
			final WolfPackLeader socialGroupsManager) {
		return new SimpleCache<Map<String, WolfPack>>(
				new ICache<Map<String, WolfPack>>() {

					@Override
					public Map<String, WolfPack> get() {
						Map<String, WolfPack> wolfpacksMap = new HashMap<String, WolfPack>();
						List<WolfPack> wolfpacksList = socialGroupsManager.getAllSocialGroups();
						for (WolfPack w : wolfpacksList) {
							wolfpacksMap.put(w.getName(), w);
						}
						return wolfpacksMap;
					}

					@Override
					public void update() {
						get();
					}
				}, cachedTimeSec);
	}

	@Provides
	@Singleton
	ICache<Map<WolfPack,List<Profile>>> provideWolfpacksMembersCache(
			@Named("server.cache.wolfpackMembers.intervalSec") int cachedTimeSec,
			final ICache<Map<String, WolfPack>> wolfpacksCache) {
		return new SimpleCache<Map<WolfPack,List<Profile>>>(
				new ICache<Map<WolfPack,List<Profile>>>() {

					@Override
					public Map<WolfPack, List<Profile>> get() {
						Map<WolfPack, List<Profile>> membersMap = new HashMap<WolfPack, List<Profile>>();
						Collection<WolfPack> wolfpacks = wolfpacksCache.get().values();
						for (WolfPack w : wolfpacks) {
							membersMap.put(w, w.getMembers());
						}
						return membersMap;
					}

					@Override
					public void update() {
						wolfpacksCache.update();
						get();
					}
				}, cachedTimeSec);
	}

	@Provides
	@Singleton
	ICache<Map<Profile,List<Post>>> provideNewsFeedCache(
			@Named("server.cache.newsfeed.intervalSec") int cachedTimeSec,
			final SocialNetwork snet,
			final ICache<Map<WolfPack,List<Profile>>> wolfpacksMembersCache,
			final ICacheWithParameter<Profile, String> profilesCache){
		return new SelfUpdatingCache<Map<Profile,List<Post>>>(
				new ICache<Map<Profile,List<Post>>>() {
					@Override
					public Map<Profile,List<Post>> get() {
						return fetchAllPosts();
					}

					private Map<Profile, List<Post>> fetchAllPosts() {
						Map<Profile, List<Post>> allPosts = new HashMap<Profile, List<Post>>();

						Map<WolfPack,List<Profile>> wolfpacksMembers = wolfpacksMembersCache.get();
						Set<Profile> profiles = new HashSet<Profile>();

						for (Map.Entry<WolfPack,List<Profile>> entry : wolfpacksMembers.entrySet()) {
							profiles.addAll(entry.getValue());
						}

						//add self profile
						Profile user = profilesCache.get("-1");
						profiles.add(user);

						for (Profile profile: profiles) {
							try {
								List<Post> posts = snet.getWall(profile).getAllPosts();
								allPosts.put(profile, posts);
							} catch (WallNotFound e) {
								System.err.println("User " + user.getName() + ": " + user.getUserId() +
										" isn't allowed to view posts of " +
										profile.getName() + ": " + profile.getUserId() + ".");
								//e.printStackTrace();
							} catch (FileNotFoundException e) {
								System.err.println("User " + user.getName() + ": " + user.getUserId() +
										" isn't allowed to view posts of " +
										profile.getName() + ": " + profile.getUserId() + ".");
								//e.printStackTrace();
							}
						}
						return allPosts;
					}

					@Override
					public void update() {
						wolfpacksMembersCache.update();
						get();
					}
				}, cachedTimeSec);
	}

}
