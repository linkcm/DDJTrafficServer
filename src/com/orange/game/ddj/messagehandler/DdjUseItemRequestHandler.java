package com.orange.game.ddj.messagehandler;


import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.common.log.ServerLog;
import com.orange.game.ddj.messagehandler.item.ItemHandleInterface;
import com.orange.game.ddj.model.DdjGameSession;
import com.orange.game.traffic.messagehandler.AbstractMessageHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.server.NotificationUtils;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.UseItemRequest;
import com.orange.network.game.protocol.message.GameMessageProtos.UseItemResponse;

public class DdjUseItemRequestHandler extends AbstractMessageHandler {

	public DdjUseItemRequestHandler(MessageEvent messageEvent) {
		super(messageEvent);
	}


	public void handleRequest(GameMessage message, Channel channel,
			GameSession gameSession) {
		DdjGameSession session = (DdjGameSession)gameSession;
		
		if (session == null){
			ServerLog.warn(0, "<UseItem> but session is null");						
			return;
		}
		
		String userId = message.getUserId();
		if (userId == null){
			ServerLog.warn(session.getSessionId(), "<UseItem> but userId is null");						
			return;
		}
		
		UseItemRequest request = message.getUseItemRequest();
		if (request == null){
			ServerLog.warn(session.getSessionId(), "<UseItem> but item request is null");			
			return;
		}
		
		int itemId = request.getItemId();	
		
		GameResultCode resultCode = GameResultCode.SUCCESS;
//		ItemHandleInterface itemHandler = getItemHandler(itemId);		
		
		// prepare response builder
		UseItemResponse.Builder useItemResponseBuilder = UseItemResponse.newBuilder()
			.setItemId(itemId);

//		resultCode = itemHandler.handleMessage(message, channel, session, userId, itemId, useItemResponseBuilder);		
		ServerLog.info(session.getSessionId(), "<UseItem> itemId="+itemId+",result="+resultCode.toString());
		
		// send use item response
		int playDirection = session.getPlayDirection();
		String nextPlayerId = session.peekNextPlayerId();
//		boolean decreaseTimerForNextPlayUser = session.getDecreaseTimeForNextPlayUser();
		
		UseItemResponse useItemResponse = useItemResponseBuilder
				.setItemId(itemId)
				.setDirection(playDirection)
				.setNextPlayUserId(nextPlayerId)
//				.setDecreaseTimeForNextPlayUser(decreaseTimerForNextPlayUser)
				.build();		
		GameMessage response = GameMessage.newBuilder()
			.setCommand(GameCommandType.USE_ITEM_RESPONSE)
			.setMessageId(message.getMessageId())
			.setResultCode(resultCode)
			.setUseItemResponse(useItemResponse)
			.setUserId(userId)
			.build();
		sendResponse(response);	
		
		
		// broadcast to all other users in the session for use item request
		if (resultCode == GameResultCode.SUCCESS){
			UseItemRequest wrappedRequest = UseItemRequest.newBuilder(request)
					.setDirection(playDirection)
					.setNextPlayUserId(nextPlayerId)
//					.setDecreaseTimeForNextPlayUser(decreaseTimerForNextPlayUser)
					.build();
			GameMessage wrappedMessage = GameMessage.newBuilder(message)
					.setUseItemRequest(wrappedRequest)
					.build();
			NotificationUtils.broadcastNotification(session, userId, wrappedMessage);
		}
	}   
	    
	private ItemHandleInterface getItemHandler(final int itemId) {
		ItemHandleInterface itemHandler = null;
		switch (itemId){
		}
		
		return itemHandler;
	}
	
	@Override
	public boolean isProcessForSessionAllocation() {
		return false;
	}

	@Override
	public boolean isProcessIgnoreSession() {
		return false;
	}

	@Override
	public boolean isProcessInStateMachine() {
		return false;
	}

}
