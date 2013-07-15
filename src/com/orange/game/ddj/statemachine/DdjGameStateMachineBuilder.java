package com.orange.game.ddj.statemachine;


import com.orange.common.statemachine.Action;
import com.orange.common.statemachine.Condition;
import com.orange.common.statemachine.DecisionPoint;
import com.orange.common.statemachine.State;
import com.orange.common.statemachine.StateMachine;
import com.orange.game.ddj.model.DdjGameSession;
import com.orange.game.ddj.statemachine.action.DdjGameAction;
import com.orange.game.ddj.statemachine.state.GameState;
import com.orange.game.ddj.statemachine.state.GameStateKey;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.server.GameEventExecutor;
import com.orange.game.traffic.statemachine.CommonGameAction;
import com.orange.game.traffic.statemachine.CommonGameCondition;
import com.orange.game.traffic.statemachine.CommonGameState;
import com.orange.game.traffic.statemachine.CommonStateMachineBuilder;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameCommandType;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserAction;

public class DdjGameStateMachineBuilder extends CommonStateMachineBuilder {

	// thread-safe singleton implementation
    private static DdjGameStateMachineBuilder builder = new DdjGameStateMachineBuilder();
    
    private DdjGameStateMachineBuilder(){		
	 } 	
    public static DdjGameStateMachineBuilder getInstance() {         	
    	return builder; 
     }
	
    public static final State INIT_STATE = new CommonGameState(GameStateKey.CREATE);

    
    private static final int START_GAME_TIMEOUT = 3; // 用户就绪到游戏开始的等待时间
    private static final int WAIT_CLAIM_TIMEOUT = 17;// 每个玩家当前轮次等待时间


    
	@Override
	public StateMachine buildStateMachine() {
		
		StateMachine stateMachine = new StateMachine();
		
		Action setStartGameTimer 		 = new CommonGameAction.CommonTimer(START_GAME_TIMEOUT, DdjGameAction.ZjhTimerType.START_GAME);
		Action setWaitClaimTimer		 = new CommonGameAction.CommonTimer(WAIT_CLAIM_TIMEOUT, DdjGameAction.ZjhTimerType.WAIT_CLAIM);
		Action notifyGameStartAndDealTimer 
												 = new DdjGameAction.NotifyGameStartAndDealTimer();
		Action notifyGameStartAndDeal  = new DdjGameAction.NotifyGameStartAndDeal(); 
		Action broadcastNextPlayerNotification
												 = new DdjGameAction.BroadcastNextPlayerNotification();
		Action completeGame				 = new DdjGameAction.CompleteGame();
		Action setShowResultTimer		 = new DdjGameAction.SetShowResultTimer();
		Action clearPlayingStatus		 = new DdjGameAction.ClearAllPlayingStatus();
		Action restartGame				 = new DdjGameAction.RestartGame();	
		Action setAlivePlayerCout		 = new DdjGameAction.SetAlivePlayerCout();
		Action timeoutBet              = new DdjGameAction.TimeoutBet();
		Action timeoutFoldCard         = new DdjGameAction.TimeoutFoldCard();
		Action setTotalBet				 = new DdjGameAction.SetTotalBet();
		Action setAllPlayerLoseGameToFalse
												 = new DdjGameAction.SetAllPlayerLoseGameToFalse();
		Action setSelectPlayerWaitTimer= new DdjGameAction.SetSelectPlayerWaitTimer();
		Action setCompleteGameWaitTimer= new DdjGameAction.setCompleteGameTimer();
		
		Condition checkUserCount = new CommonGameCondition.CheckUserCount();
		 
		stateMachine.addState(INIT_STATE)
						.addAction(initGame)
						.addAction(clearTimer)
						.addTransition(GameCommandType.LOCAL_NEW_USER_JOIN, GameStateKey.CHECK_USER_COUNT);
		
		stateMachine.addState(new GameState(GameStateKey.CHECK_USER_COUNT))
						.addAction(clearTimer)
						.setDecisionPoint(new DecisionPoint(checkUserCount) {
								@Override
								public Object decideNextState(Object context){
									GameSession session = (GameSession)context;
									int userCount = condition.decide(context);
									if (userCount == 0){
										if ( session.isCreatedByUser() ) {
											GameEventExecutor.getInstance().executeForSessionRealease(session.getSessionId());
											return null;
										}
										session.resetGame();
										return GameStateKey.CREATE;
									}
									else if (userCount <= 1){
										session.finishGame();
										return GameStateKey.ONE_USER_WAITING;
									}
									else{ // more than one user, can start game
										return GameStateKey.WAIT_FOR_START_GAME;
									}
								}
						});
		
		
		
		stateMachine.addState(new GameState(GameStateKey.ONE_USER_WAITING))
						.addAction(setOneUserWaitTimer)
						.addAction(prepareRobot)
						.addTransition(GameCommandType.LOCAL_NEW_USER_JOIN, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_USER_QUIT, GameStateKey.CHECK_USER_COUNT) // 房间等待状态，有用户退出，但房间还有人
						.addTransition(GameCommandType.LOCAL_ALL_USER_QUIT, GameStateKey.CHECK_USER_COUNT) // 房间等待状态，有用户退出，房间为空
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.KICK_PLAY_USER)
						.addAction(clearTimer)
						.addAction(clearRobotTimer);				
		
		stateMachine.addState(new GameState(GameStateKey.KICK_PLAY_USER))
						.addAction(kickPlayUser)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								return GameStateKey.CHECK_USER_COUNT;	// goto check user count state directly
							}
						});
		
		
		stateMachine.addState(new GameState(GameStateKey.WAIT_FOR_START_GAME))
//						.addAction(startGame)
//						.addAction(setAlivePlayerCout)
						.addAction(setStartGameTimer)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_OTHER_USER_QUIT, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT, GameStateKey.CHECK_USER_COUNT)
						.addTransition(GameCommandType.LOCAL_USER_QUIT, GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.DEAL)				
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)
						.addAction(clearTimer);
		
		
		
		stateMachine.addState(new GameState(GameStateKey.DEAL))
						.addAction(startGame)
						.addAction(setAlivePlayerCout)
						.addAction(setTotalBet)
						.addAction(setAllPlayerLoseGameToFalse)
						.addAction(notifyGameStartAndDealTimer)
						.addAction(notifyGameStartAndDeal)
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT, GameStateKey.PLAY_USER_QUIT) // 此时未选择玩家，没有PLAY_USER_QUIT
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT,GameStateKey.COMPLETE_GAME)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.SELECT_NEXT_PLAYER)
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT) // 激发此事件说明房间至少剩2个以上玩家，且该退出玩家不是当前轮，所以不动
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)  // 旁观者加入
						.addAction(clearTimer);
		
		
		stateMachine.addState(new GameState(GameStateKey.SELECT_PLAYER_WAIT_TIMER))
						.addAction(setSelectPlayerWaitTimer)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.COMPLETE_GAME) //有玩家退出后，存活人数为1，结束游戏
						.addTransition(GameCommandType.LOCAL_NOT_CURRENT_TURN_FOLD_CARD,GameStateKey.COMPLETE_GAME) // 非当前轮玩家弃牌并且存活人数为1,导致游戏结束
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.SELECT_NEXT_PLAYER) //时间到，选择下一玩家 
						.addEmptyTransition(GameCommandType.LOCAL_PLAY_USER_QUIT) // 此时当前玩家已完成其轮次，下一用户还未选择，保持当前状态，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT) // 别的玩家退出，但还未结束游戏，保持当前状态，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_FOLD_CARD) // 当前玩家弃牌，保持当前状态，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN) 
						.addAction(clearTimer);	
		
		stateMachine.addState(new GameState(GameStateKey.SELECT_NEXT_PLAYER))
						.addAction(clearTimer) // 如果是从BET, FOLD_CARD, TIMEOUT_FOLD_CARD, COMPARE_CARD跳转来，先清空其定时器
						.addAction(selectPlayUser)
						.setDecisionPoint(new DecisionPoint(null) {
							@Override
							public Object decideNextState(Object context){
								DdjGameSession session = (DdjGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount();
								if ( alivePlayerCount <= 1 || session.shallCompleteGame())
									return GameStateKey.COMPLETE_GAME;
								else {
									GameUser user = session.getCurrentPlayUser();
									if (session.getUserCount() <= 1){
										return GameStateKey.CHECK_USER_COUNT;
									}
									if (user != null ){
										return GameStateKey.WAIT_NEXT_PLAYER_PLAY;
									}
									else{
										return GameStateKey.SELECT_NEXT_PLAYER;
									}
								}
							}
						});				
		
		
		stateMachine.addState(new GameState(GameStateKey.WAIT_NEXT_PLAYER_PLAY))
						.addAction(broadcastNextPlayerNotification)
						.addAction(setWaitClaimTimer)
						.addEmptyTransition(GameCommandType.LOCAL_CHECK_CARD) // 看牌 
						.addEmptyTransition(GameCommandType.LOCAL_SHOW_CARD) // 亮牌
						.addEmptyTransition(GameCommandType.LOCAL_CHANGE_CARD) // 换牌
						.addTransition(GameCommandType.LOCAL_BET, GameStateKey.PLAYER_BET) // 跟注， 加注， 跟到底 
						.addTransition(GameCommandType.LOCAL_FOLD_CARD, GameStateKey.PLAYER_FOLD_CARD) // 弃牌
						.addTransition(GameCommandType.LOCAL_COMPARE_CARD,GameStateKey.PLAYER_COMPARE_CARD) // 比牌
						.addTransition(GameCommandType.LOCAL_PLAY_USER_QUIT,GameStateKey.PLAY_USER_QUIT)
						.addTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT, GameStateKey.COMPLETE_GAME)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.TIMEOUT_ACTION) // 超时无动作，根据设置作出选择
						.addTransition(GameCommandType.LOCAL_NOT_CURRENT_TURN_FOLD_CARD,GameStateKey.COMPLETE_GAME) // 非当前轮玩家弃牌导致游戏可结束
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT) // 激发此事件说明房间至少剩2个以上玩家，且该退出玩家不是当前轮，所以不动
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)  // 旁观者加入
						.addAction(clearTimer);	
		
		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_BET))
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						 }); 
		
		stateMachine.addState(new GameState(GameStateKey.TIMEOUT_ACTION))
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								DdjGameSession session = (DdjGameSession)context;
								if ( session.chooseCurrentPlayerTimeoutAction() == PBZJHUserAction.BET)
									return GameStateKey.TIMEOUT_BET;
								else 
									return GameStateKey.TIMEOUT_FOLD_CARD;
							}
						 }); 

		stateMachine.addState(new GameState(GameStateKey.TIMEOUT_BET))
//						.addAction(incUserZombieTimeOut)   // 不要增加UserZombieTimeout，因为这是用户主动设定的
						.addAction(timeoutBet)
							.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								DdjGameSession session = (DdjGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount(); // 此时获得的aliveCount不算上弃牌的用户
								if ( alivePlayerCount < 2 ) 
									return GameStateKey.COMPLETE_GAME;
								else 
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});
		
		
		stateMachine.addState(new GameState(GameStateKey.TIMEOUT_FOLD_CARD))
						.addAction(incUserZombieTimeOut)
						.addAction(timeoutFoldCard)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								DdjGameSession session = (DdjGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount(); // 此时获得的aliveCount不算上弃牌的用户
								if ( alivePlayerCount < 2 ) 
									return GameStateKey.COMPLETE_GAME;
								else 
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});

		
		stateMachine.addState(new GameState(GameStateKey.PLAYER_FOLD_CARD))
						.addAction(clearTimer) // 清掉该用户的计时器
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								DdjGameSession session = (DdjGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount(); // 此时获得的aliveCount不算上弃牌的用户
								if ( alivePlayerCount == 1 ) // 弃牌后只剩一个存活玩家 
									return GameStateKey.COMPLETE_GAME;
								else 
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});
		

		stateMachine.addState(new GameState(GameStateKey.PLAYER_COMPARE_CARD))
						.addAction(clearTimer) // 清掉该用户的计时器
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){
								DdjGameSession session = (DdjGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount();
								if ( alivePlayerCount == 1 ) // 弃牌后只剩一个存活玩家
									return GameStateKey.COMPLETE_GAME_WAIT_TIMER;
								else 
									return GameStateKey.SELECT_PLAYER_WAIT_TIMER;
							}
						});
		
		
		stateMachine.addState(new GameState(GameStateKey.PLAY_USER_QUIT))
						.addAction(kickPlayUser)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){					
								DdjGameSession session = (DdjGameSession)context;
								int alivePlayerCount = session.getAlivePlayerCount(); // 还没输的人
								int gameUserCount = session.getPlayUserCount(); // 所有在玩的人
								if ( alivePlayerCount == 1 || gameUserCount == 1)
									return GameStateKey.COMPLETE_GAME;	
								else
									return GameStateKey.SELECT_NEXT_PLAYER;
							}
						});
					
		
		stateMachine.addState(new GameState(GameStateKey.COMPLETE_GAME_WAIT_TIMER))
						.addAction(setCompleteGameWaitTimer)
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.COMPLETE_GAME) //时间到，结束处理程序 
						.addEmptyTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT) //已经是结束等待时间，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_PLAY_USER_QUIT) //已经是结束等待时间，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT) //已经是结束等待时间，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_FOLD_CARD) //已经是结束等待时间，延后处理
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN);
		
		stateMachine.addState(new GameState(GameStateKey.COMPLETE_GAME))
						.addAction(completeGame)
						.setDecisionPoint(new DecisionPoint(null){
							@Override
							public Object decideNextState(Object context){					
								return GameStateKey.SHOW_RESULT;	
							}
						});
	
		
		
		stateMachine.addState(new GameState(GameStateKey.SHOW_RESULT))
						.addAction(setShowResultTimer)
						.addAction(finishGame)  // finishGame 后房间状态又处于等待了
						.addEmptyTransition(GameCommandType.LOCAL_NEW_USER_JOIN)	
						.addEmptyTransition(GameCommandType.LOCAL_PLAY_USER_QUIT)	
						.addEmptyTransition(GameCommandType.LOCAL_OTHER_USER_QUIT)	
						.addEmptyTransition(GameCommandType.LOCAL_ALL_OTHER_USER_QUIT)	
						.addEmptyTransition(GameCommandType.LOCAL_ALL_USER_QUIT)	
						.addEmptyTransition(GameCommandType.LOCAL_USER_QUIT)	
						.addTransition(GameCommandType.LOCAL_TIME_OUT, GameStateKey.CHECK_USER_COUNT)
						.addAction(clearPlayingStatus)
						.addAction(kickZombieUser)
						.addAction(clearTimer)
						.addAction(restartGame);
	
		stateMachine.printStateMachine();		
		
		return stateMachine;
	} 
    	
}