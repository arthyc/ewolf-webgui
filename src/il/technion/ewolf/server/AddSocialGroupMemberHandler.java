package il.technion.ewolf.server;

import il.technion.ewolf.WolfPack;
import il.technion.ewolf.WolfPackLeader;
import il.technion.ewolf.socialfs.Profile;
import il.technion.ewolf.socialfs.SocialFS;
import il.technion.ewolf.socialfs.UserID;
import il.technion.ewolf.socialfs.UserIDFactory;
import il.technion.ewolf.socialfs.exception.ProfileNotFoundException;
import il.technion.ewolf.stash.exception.GroupNotFoundException;

import java.io.File;
import java.io.IOException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.FileEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class AddSocialGroupMemberHandler implements HttpRequestHandler {
	private final WolfPackLeader socialGroupsManager;
	private final SocialFS socialFS;
	private final UserIDFactory userIDFactory;
	private final String hostName;
	private final String port;

	
	@Inject
	public AddSocialGroupMemberHandler(SocialFS socialFS, WolfPackLeader socialGroupsManager,
			UserIDFactory userIDFactory, @Named("server.port") String port,
			@Named("server.host.name") String hostName) {
		this.socialGroupsManager = socialGroupsManager;
		this.socialFS = socialFS;
		this.userIDFactory = userIDFactory;
		this.hostName = hostName;
		this.port = port;
	}

	//XXX req of type POST with "/addSocialGroupMember/{groupName}" URI and body containing userID=id
	@Override
	public void handle(HttpRequest req, HttpResponse res,
			HttpContext context) throws HttpException, IOException {
		//TODO move adding headers to response intercepter
		res.addHeader("Server", "e-WolfNode");
		res.addHeader("Content-Type", "application/json");
		
		//get user ID from the body
		String dataSet = EntityUtils.toString(((HttpEntityEnclosingRequest)req).getEntity());
		String key = dataSet.substring(dataSet.indexOf("=") + 1).trim();
		UserID uid = userIDFactory.getFromBase64(key);

		Profile profile = null;
		try {
			profile = socialFS.findProfile(uid);			
		} catch (ProfileNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("Profile with UserID " + uid + "not found");
			res.setStatusCode(HttpStatus.SC_NOT_FOUND);
			res.setEntity(new FileEntity(new File("404.html"),"text/html"));
			return;
		}
		
		//get social group name from URI
		String reqURI = req.getRequestLine().getUri();
		String[] splitedURI = reqURI.split("/");
		String groupName = splitedURI[splitedURI.length-1];
		
		WolfPack socialGroup = socialGroupsManager.findSocialGroup(groupName);
		if (socialGroup == null) {
			res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
			return;
		}
		
		try {
			socialGroup.addMember(profile);
		} catch (GroupNotFoundException e) {
			System.out.println("Social group" + groupName + "not found");
			res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		res.setStatusCode(HttpStatus.SC_SEE_OTHER);
		//FIXME where to redirect?
		res.setHeader("Location", hostName + ":" + port + "/viewSocialGroupMembers/" + groupName);
	}

}
