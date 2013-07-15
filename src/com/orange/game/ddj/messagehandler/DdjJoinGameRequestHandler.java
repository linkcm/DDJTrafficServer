package com.orange.game.ddj.messagehandler;

import java.util.List;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.MessageEvent;

import com.orange.game.ddj.model.DdjGameSession;
import com.orange.game.traffic.messagehandler.room.JoinGameRequestHandler;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.network.game.protocol.message.GameMessageProtos.GameMessage;
import com.orange.network.game.protocol.message.GameMessageProtos.JoinGameResponse;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHGameState;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;


public class DdjJoinGameRequestHandler extends JoinGameRequestHandler {

	
	public DdjJoinGameRequestHandler(MessageEvent event) {
		super(event);
	}
	
	@Override
	public void handleRequest(GameMessage message, Channel channel, GameSession requestSession) {

		DdjGameSession session = (DdjGameSession)processRequest(message, channel, requestSession);

	}
	
	@Override
	public JoinGameResponse responseSpecificPart(JoinGameResponse.Builder builder, GameSession session) {
		
		JoinGameResponse response;
		
		if (session.isGamePlaying()) {
			List<PBZJHUserPlayInfo> userPlayInfos = ((DdjGameSession)session).getUserPlayInfo();
			int totalBet = ((DdjGameSession)session).getTotalBet();
			int singleBet =((DdjGameSession)session).getSingleBet();
			
			PBZJHGameState state = PBZJHGameState.newBuilder()
								.setTotalBet(totalBet)
								.setSingleBet(singleBet)
								.addAllUsersInfo(userPlayInfos)
								.build();
			
			
			response = builder.setZjhGameState(state)
									.build();
			
//			ServerLog.info(session.getSessionId(), "!!!!!!!!!!!! JoinGameResponse = " + response);
			
			return response;
		   
		} else {	
			response = builder.build();
			return response;
		}
	}
}
