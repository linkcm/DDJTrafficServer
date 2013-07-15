package com.orange.game.ddj.robot.client;

import com.orange.game.model.dao.User;
import com.orange.game.traffic.robot.client.AbstractRobotClient;
import com.orange.game.traffic.robot.client.AbstractRobotManager;

public class DdjRobotManager extends AbstractRobotManager {

	public DdjRobotManager() {
		super();
	}
	
	@Override
	public AbstractRobotClient createRobotClient(User robotUser, int sessionId,
			int index) {
		return new DdjRobotClient(robotUser, sessionId, index);
	}
}
