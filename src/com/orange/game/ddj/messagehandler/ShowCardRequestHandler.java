package com.orange.game.ddj.messagehandler;

import java.util.List;

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
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.ShowCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.ShowCardResponse;

public class ShowCardRequestHandler extends AbstractMessageHandler {

	public ShowCardRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}
	
	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {

		GameResultCode resultCode;
		String userId = message.getUserId();
		ShowCardRequest request = message.getShowCardRequest();
		
		if (session == null){
			logger.info("<ShowCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<ShowCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else if ( ! session.safeGetCurrentPlayUserId().equals(userId)) {
			logger.info("<ShowCardRequestHandler> Not current player !!!");
			resultCode = GameResultCode.ERROR_USER_NOT_CURRENT_PLAY_USER;
		}
		else {
			List<Integer> cardIds = request.getCardIdsList();
			// do the real job.
			resultCode = ((DdjGameSession)session).showCard(userId, cardIds); 
		}
		
		
		// Now build the response.
		// Empty showCard response
		ShowCardResponse showCardResponse = ShowCardResponse.newBuilder().build();
		
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.SHOW_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setShowCardRequest(request) 
				.setUserId(userId)
				.setShowCardResponse(showCardResponse)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// broadcast to all other players.		
			boolean sendToSelf = false;
			GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.SHOW_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setShowCardRequest(request);
			
			NotificationUtils.broadcastNotification(session, builder, sendToSelf);
			
			// fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_SHOW_CARD, session.getSessionId(), userId);
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
