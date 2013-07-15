package com.orange.game.ddj.server;

import com.orange.common.statemachine.StateMachine;
import com.orange.game.ddj.model.DdjGameSessionManager;
import com.orange.game.ddj.robot.client.DdjRobotManager;
import com.orange.game.ddj.statemachine.DdjGameStateMachineBuilder;
import com.orange.game.traffic.robot.client.AbstractRobotManager;
import com.orange.game.traffic.robot.client.RobotService;
import com.orange.game.traffic.server.GameServer;

public class DdjGameServer {
	
	public static void main(String[] args) {

		final AbstractRobotManager robotManager = new DdjRobotManager();
		RobotService.getInstance().initRobotManager(robotManager);
		
		// init data
		StateMachine ZjhStateMachine = DdjGameStateMachineBuilder.getInstance().buildStateMachine();
		DdjGameSessionManager sessionManager = new DdjGameSessionManager();
		
		// create server
		GameServer server = new GameServer(new DdjGameServerHandler(), ZjhStateMachine,
				sessionManager, robotManager);
		
		// start server
		server.start();
		
	}
}
