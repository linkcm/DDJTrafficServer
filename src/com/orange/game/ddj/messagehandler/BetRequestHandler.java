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
import com.orange.network.game.protocol.message.GameMessageProtos.BetRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.BetResponse;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;


public class BetRequestHandler extends AbstractMessageHandler {

	
	public BetRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}

	@Override
	public void handleRequest(GameMessage message, Channel channel,
			GameSession session) {
		
		GameResultCode resultCode;
		String userId = message.getUserId();
		BetRequest request = message.getBetRequest();
		
		if (session == null){
			logger.info("<BetRequestHandler> Session is null !!!");
			resultCode = GameResultCode.ERROR_NO_SESSION_AVAILABLE;
		}
		else if (userId == null || session.getCurrentPlayUserId() == null){
			logger.info("<BetRequestHandler> UserId is null !!!");
			resultCode = GameResultCode.ERROR_USERID_NULL;
		}
		else if ( ! session.getCurrentPlayUserId().equals(userId)) {
			logger.info("<BetRequestHandler> Not current player !!!");
			resultCode = GameResultCode.ERROR_USER_NOT_CURRENT_PLAY_USER;
		}
		else {
			int singleBet = request.getSingleBet(); // 单注
			int count = request.getCount(); // 注数
			boolean isAutoBet = request.getIsAutoBet(); // 是否自动跟注
			// Do the real job.
			resultCode = ((DdjGameSession)session).bet(userId, singleBet, count,isAutoBet); 
		}
		
		
		// Now build the response.
		// Empty bet response
		BetResponse betResponse = BetResponse.newBuilder().build();
		
		GameMessage response = GameMessage.newBuilder()
				.setCommand(GameCommandType.BET_RESPONSE)
				.setMessageId(message.getMessageId())
				.setResultCode(resultCode)
				.setUserId(userId)
				.setBetRequest(request)
				.setBetResponse(betResponse)
				.build();
		
		// Send it.
		sendResponse(response);

		
		if (resultCode == GameResultCode.SUCCESS){
			// Broadcast to all other players.		
			GameMessage.Builder builder = GameMessageProtos.GameMessage.newBuilder()
					.setCommand(GameCommandType.BET_REQUEST)
					.setMessageId(GameEventExecutor.getInstance().generateMessageId())
					.setSessionId(session.getSessionId())
					.setUserId(userId)
					.setBetRequest(request);
			
			boolean sendToSelf = false;
			NotificationUtils.broadcastNotification(session, builder, sendToSelf);
			
			// Fire event
			GameEventExecutor.getInstance().fireAndDispatchEvent(GameCommandType.LOCAL_BET, session.getSessionId(), userId);
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
