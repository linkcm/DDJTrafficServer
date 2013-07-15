package com.orange.game.ddj.messagehandler;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.ddj.model.DdjGameSession;
import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.message.GameMessageProtos.CompareCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.CompareCardResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class CompareCardReuqestHandler extends AbstractMessageHandler {

	public CompareCardReuqestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		GameResultCode resultCode;
		String userId = message.getUserId();
		CompareCardRequest request = message.getCompareCardRequest();
		String toUserId = null;
		
		if (session == null){
			logger.info("<CompareCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<CompareCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else if ( ! session.safeGetCurrentPlayUserId().equals(userId)) {
			logger.info("<CompareCardRequestHandler> Not current player !!!");
			resultCode = GameResultCode.ERROR_USER_NOT_CURRENT_PLAY_USER;
		}
		else {
			// do the real job.
			toUserId = request.getToUserId();
			resultCode = ((DdjGameSession)session).compareCard(userId, toUserId); 
		}
		
		
		// Now build the response.
		GameMessage responseMessage;
		GameMessage.Builder builder = GameMessage.newBuilder()
				.setCommand(GameCommandType.COMPARE_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId);
		
		if ( resultCode.equals(GameResultCode.SUCCESS) ){
			CompareCardResponse response = CompareCardResponse.newBuilder()
					.addAllUserResult(((DdjGameSession)session).getCompareResults())
					.build();
			
			responseMessage = builder.setCompareCardResponse(response).build();
		} else {
			responseMessage = builder.build();
		}
		
		// Send it.
		sendResponse(responseMessage);

		// Broadcast to all other players.
		if (resultCode == GameResultCode.SUCCESS){
		
			// Broadcast response to  all other players.
			NotificationUtils.broadcastNotification(session,userId, responseMessage);
			
			// Fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_COMPARE_CARD, session.getSessionId(), userId);
			}

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
