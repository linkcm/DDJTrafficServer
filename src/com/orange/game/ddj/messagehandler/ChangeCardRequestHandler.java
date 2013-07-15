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
import com.orange.network.game.protocol.message.GameMessageProtos.ChangeCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.ChangeCardResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;


public class ChangeCardRequestHandler extends AbstractMessageHandler {

	
	public ChangeCardRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}
	
	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {

		GameResultCode resultCode;
		String userId = message.getUserId();
		ChangeCardRequest request = message.getChangeCardRequest();
		ChangeCardResponse changeCardResponse = null;
		int toChangeCardId = request.getCardId();
		
		if (session == null){
			logger.info("<ChangeCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<ChangeCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			resultCode = ((DdjGameSession)session).changeCard(userId, toChangeCardId); 
		}
		
		
		// Now build the response.
		if ( resultCode == GameResultCode.SUCCESS ) {
		   changeCardResponse = ((DdjGameSession)session).getChangeCardResponse();
		} else {
			// 如果换牌不成功，仍然返回旧牌
			changeCardResponse = ChangeCardResponse.newBuilder()
									.setOldCardId(toChangeCardId)
									.setNewPoker(((DdjGameSession)session).pokerIdToPBPoker(toChangeCardId))
									.build();
		}
		
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.CHANGE_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId)
				.setChangeCardResponse(changeCardResponse)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			GameMessage broadcastMessage = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.CHANGE_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setChangeCardResponse(changeCardResponse)
					.build();
			
			NotificationUtils.broadcastNotification(session, userId, broadcastMessage);
			
			// Fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_CHANGE_CARD, session.getSessionId(), userId);
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
