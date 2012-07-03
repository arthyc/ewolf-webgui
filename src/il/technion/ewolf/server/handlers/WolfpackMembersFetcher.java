package il.technion.ewolf.server.handlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Inject;

import il.technion.ewolf.WolfPack;
import il.technion.ewolf.WolfPackLeader;
import il.technion.ewolf.socialfs.Profile;

public class WolfpackMembersFetcher implements JsonDataHandler {
	private final WolfPackLeader socialGroupsManager;

	@Inject
	public WolfpackMembersFetcher(WolfPackLeader socialGroupsManager) {
		this.socialGroupsManager = socialGroupsManager;
	}
	
	class ProfileData {
		String name;
		String id;
	
		ProfileData(String name, String id) {
			this.name = name;
			this.id = id;
		}
	}

	private class JsonReqWolfpackMembersParams {
//		If wolfpackName field wasn't sent with the request then
//		the response list will contain all the members of all the "logged in" user wolfpacks
		String wolfpackName;
	}

	/**
	 * @param	jsonReq	serialized object of JsonReqWolfpackMembersParams class
	 * @return	list of ProfileData objects. Each object contains name and user ID.
	 */
	@Override
	public Object handleData(JsonElement jsonReq) {
		Gson gson = new Gson();
		//TODO handle JsonSyntaxException
		JsonReqWolfpackMembersParams jsonReqParams =
				gson.fromJson(jsonReq, JsonReqWolfpackMembersParams.class);

		List<ProfileData> resList = new ArrayList<ProfileData>();
		List<WolfPack> wolfpacks = new ArrayList<WolfPack>();

		if (jsonReqParams.wolfpackName == null) {
			wolfpacks = socialGroupsManager.getAllSocialGroups();
		} else {
			wolfpacks.add(socialGroupsManager.findSocialGroup(jsonReqParams.wolfpackName));
		}
		
		Set<Profile> profiles = new HashSet<Profile>();
		for (WolfPack w : wolfpacks) {
			profiles.addAll(w.getMembers());
		}
		
		for (Profile profile: profiles) {
			resList.add(new ProfileData(profile.getName(), profile.getUserId().toString()));
		}
		return resList;
	}

}
