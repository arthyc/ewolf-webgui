package il.technion.ewolf.server.jsonDataHandlers;

import static il.technion.ewolf.server.EWolfResponse.RES_BAD_REQUEST;
import static il.technion.ewolf.server.EWolfResponse.RES_GENERIC_ERROR;
import static il.technion.ewolf.server.EWolfResponse.RES_INTERNAL_SERVER_ERROR;
import il.technion.ewolf.server.EWolfResponse;
import il.technion.ewolf.server.EwolfServer;
import il.technion.ewolf.server.ServerResources;
import il.technion.ewolf.server.ServerResources.EwolfConfigurations;
import il.technion.ewolf.server.jsonDataHandlers.LoginHandler.LoginResponse;

import org.apache.commons.configuration.ConfigurationException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public class CreateAccountHandler implements IJsonDataHandler {

	private EwolfServer ewolfServer;
	private String configFile;

	public CreateAccountHandler(EwolfServer ewolfServer, String configFile) {
		this.configFile = configFile;
		this.ewolfServer = ewolfServer;
	}

	private static class JsonReqCreateAccountParams {
		String username;
		String name;
		String password;
	}

	static class CreateAccountResponse extends EWolfResponse {
		public CreateAccountResponse(String result, String errorMessage) {
			super(result, errorMessage);
		}

		public CreateAccountResponse(String result) {
			super(result);
		}

		public CreateAccountResponse() {
		}
	}

	@Override
	public EWolfResponse handleData(JsonElement jsonReq) {
		EwolfConfigurations configurations;
		try {
			configurations = ServerResources.getConfigurations(configFile);
		} catch (ConfigurationException e) {
			e.printStackTrace();
			return new LoginResponse(RES_INTERNAL_SERVER_ERROR);
		}
		if (configurations.name != null && configurations.username != null
				&& configurations.password != null) {
			return new CreateAccountResponse(RES_BAD_REQUEST, "User already exists.");
		}

		Gson gson = new Gson();
		JsonReqCreateAccountParams jsonReqParams;
		try {
			jsonReqParams = gson.fromJson(jsonReq, JsonReqCreateAccountParams.class);
		} catch (Exception e) {
			e.printStackTrace();
			return new CreateAccountResponse(RES_BAD_REQUEST);
		}

		if (jsonReqParams.username == null || jsonReqParams.name == null
				|| jsonReqParams.password == null) {
			return new CreateAccountResponse(RES_BAD_REQUEST,
					"Must specify username, name and password.");
		}
		if (!jsonReqParams.username.matches("[a-zA-z0-9]+")) {
			return new CreateAccountResponse(RES_BAD_REQUEST,
					"Username can contain only digits and letters.");
		}
		try {
			ServerResources.setUserConfigurations(configFile, jsonReqParams.username,
					jsonReqParams.name, jsonReqParams.password);
		} catch (ConfigurationException e) {
			e.printStackTrace();
			return new CreateAccountResponse(RES_GENERIC_ERROR,
					"Error while saving to the properties file.");
		}

		while (!ewolfServer.isReady) {

		}
		return new CreateAccountResponse();
	}

}
