package com.orange.game.ddj.messagehandler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.ddj.model.DdjGameSession;
import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.TimeoutSettingRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.TimeoutSettingResponse;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserAction;

public class TimeoutSettingRequestHandler extends AbstractMessageHandler {

	public TimeoutSettingRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {

		GameResultCode resultCode;
		String userId = message.getUserId();
		TimeoutSettingRequest request = message.getTimeoutSettingRequest();
		
		if (session == null){
			logger.info("<TimeoutSettingRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<TimeoutSettingRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
//		else if ( ! session.getCurrentPlayUserId().equals(userId)) {
//			logger.info("<TimeoutSettingRequestHandler> Not current player !!!");
//			resultCode = GameResultCode.ERROR_USER_NOT_CURRENT_PLAY_USER;
//		}
		else {
			PBZJHUserAction action = PBZJHUserAction.FOLD_CARD;
			if ( request.hasAction() ) {
				action = request.getAction();
			}
			resultCode = ((DdjGameSession)session).setTimeoutAction(userId, action);
		}
		
		
		// Now build the response.
		GameMessage responseMessage;
		GameMessage.Builder builder = GameMessage.newBuilder()
						.setCommand(GameCommandType.TIMEOUT_SETTING_RESPONSE)
						.setMessageId(message.getMessageId())
						.setResultCode(resultCode)
						.setUserId(userId);
				
		if ( resultCode.equals(GameResultCode.SUCCESS) ){
				TimeoutSettingResponse response = TimeoutSettingResponse.newBuilder().build();
				responseMessage = builder.setTimeoutSettingResponse(response).build();
		} else {
				responseMessage = builder.build();
		}
				
		// Send it.
		sendResponse(responseMessage);

		// Ok, that's all. We needn't broadcast it or fire a event for state machine either.
	}

	
	@Override
	public boolean isProcessIgnoreSession() {
		return false;
	}

	@Override
	public boolean isProcessInStateMachine() {
		return false;
	}

	@Override
	public boolean isProcessForSessionAllocation() {
		return false;
	}

}
