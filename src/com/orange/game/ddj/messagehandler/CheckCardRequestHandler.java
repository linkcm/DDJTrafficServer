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
import com.orange.network.game.protocol.message.GameMessageProtos;
import com.orange.network.game.protocol.message.GameMessageProtos.CheckCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.CheckCardResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class CheckCardRequestHandler extends AbstractMessageHandler {

	public CheckCardRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		GameResultCode resultCode;
		String userId = message.getUserId();
		CheckCardRequest request = message.getCheckCardRequest();
		
		if (session == null){
			logger.info("<CheckCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<CheckCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			// do the real job.
			resultCode = ((DdjGameSession)session).checkCard(userId); 
		}
		
		
		// Now build the response.
		// Empty checkCard response
		CheckCardResponse checkCardResponse = CheckCardResponse.newBuilder().build();
		
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.CHECK_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId)
				.setCheckCardResponse(checkCardResponse)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			GameMessage broadcastMessage = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.CHECK_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setCheckCardRequest(request)
					.build();
			
			NotificationUtils.broadcastNotification(session, userId, broadcastMessage);
			
			// Player can check card anytime, so need to check is it my turn to
			// decide whether to fire the event to make the state machine transit
			String currentPlayUserId = session.getCurrentPlayUserId();
			if ( currentPlayUserId != null && currentPlayUserId.equals(userId)) { 
				// Fire event
				GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_CHECK_CARD, session.getSessionId(), userId);
			}
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
