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
import com.orange.network.game.protocol.message.GameMessageProtos.FoldCardRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.FoldCardResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;

public class FoldCardRequestHandler extends AbstractMessageHandler {

	public FoldCardRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {

//		ServerLog.info(session.getSessionId(), "Get foldCard Request = " + message.toString());
		GameResultCode resultCode;
		String userId = message.getUserId();
		FoldCardRequest request = message.getFoldCardRequest();
		
		if (session == null){
			logger.info("<FoldCardRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null){
			logger.info("<FoldCardRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else {
			// do the real job.
			resultCode = ((DdjGameSession)session).foldCard(userId); 
		}
		
		
		// Now build the response.
		// Empty foldCard response
		FoldCardResponse foldCardResponse = FoldCardResponse.newBuilder().build();
		
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.FOLD_CARD_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setFoldCardRequest(request) // insert the request to broadcast to all other players.
				.setUserId(userId)
				.setFoldCardResponse(foldCardResponse)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			GameMessage broadcastMessage = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.FOLD_CARD_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setFoldCardRequest(request)
					.build();
			
			NotificationUtils.broadcastNotification(session, userId, broadcastMessage);
			
			// Player can check card anytime, so need to check is it my turn to
			// decide whether to fire the event to make the state machine transit
			if ( session.safeGetCurrentPlayUserId().equals(userId)) { 
				// Fire event
				GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_FOLD_CARD, session.getSessionId(), userId);
			} 
			else if (((DdjGameSession)session).getAlivePlayerCount() <= 1 ) {
					// 非当前轮玩家弃牌，如果存活玩家只剩一个，直接可以结束游戏，当前轮玩家获胜。
					GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_NOT_CURRENT_TURN_FOLD_CARD, session.getSessionId(), userId);
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
