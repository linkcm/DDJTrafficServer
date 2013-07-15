package com.orange.game.ddj.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.math.RandomUtils;

import com.orange.common.log.ServerLog;
import com.orange.common.mongodb.MongoDBClient;
import com.orange.common.utils.IntegerUtil;
import com.orange.game.constants.DBConstants;
import com.orange.game.ddj.statemachine.DdjGameStateMachineBuilder;
import com.orange.game.model.manager.UserManager;
import com.orange.game.traffic.model.dao.GameSession;
import com.orange.game.traffic.model.dao.GameUser;
import com.orange.game.traffic.service.UserGameResultService;
import com.orange.network.game.protocol.constants.GameConstantsProtos.GameResultCode;
import com.orange.network.game.protocol.message.GameMessageProtos.ChangeCardResponse;
import com.orange.network.game.protocol.model.GameBasicProtos.PBUserResult;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerRank;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerSuit;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserAction;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHUserPlayInfo;

// static import~
import static com.orange.game.ddj.model.DdjGameConstant.*;
import static com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType.*;

public class DdjGameSession extends GameSession {
	
	// 扑克堆
	private int[] pokerPool = new int[DdjGameConstant.ALL_CARD_NUM];
	private int pokerPoolCursor = 0;
	
	// 指未弃牌，游戏未输，游戏过程中没退出的玩家个数
	private AtomicInteger alivePlayerCount = new AtomicInteger(0);
	// 房间当前单注值， 默认值跟房间类型有关
	private volatile int singleBet;
	// 房间当前总注
	private volatile int totalBet;
	// 房间初始单注值
	private final int  initSingleBet ;
	// 房间总注上限
	private final int totalBetThreshold;
	// 房间最大筹码值
	private final int maximumAnte ;
	// 是否有人拿了豹子牌？ 
	private boolean someOneGotKing;
	 
	// 每个玩家的扑克
	private Map<String, List<PBPoker>> userPokersMap = new ConcurrentHashMap<String, List<PBPoker>>();
	// 每个玩家的牌型
	private Map<String, PBZJHCardType> cardTypeMap = new ConcurrentHashMap<String, PBZJHCardType>();
	// 每个玩家的牌面值和花色值信息, 与userPokersMap有重复。方便比牌时操作。
	private Map<String, Integer> rankMaskMap = new ConcurrentHashMap<String, Integer>();
	private Map<String, Integer> suitMaskMap = new ConcurrentHashMap<String, Integer>();
	// 每个玩家的扑克是否亮牌， 方便亮牌操作
	private Map<String, Long> faceStatusMap = new ConcurrentHashMap<String, Long>();
	// 对子牌面值(如果有对子的话), 方便比牌操作
	private Map<String, Integer> pairRankMap = new ConcurrentHashMap<String, Integer>();
	// 每个玩家的总注
	private Map<String, Integer> totalBetMap = new ConcurrentHashMap<String, Integer>();
	// 每个玩家的下注次数（抵消掉换牌用去的次数，每三次可换一张牌)
	private Map<String, Integer> playerBetTimes = new ConcurrentHashMap<String, Integer>();
	// 每个玩家的超时选择: 弃牌或跟牌
	private Map<String, PBZJHUserAction> timeoutSettingMap = new ConcurrentHashMap<String, PBZJHUserAction>();
	// 存放比牌结果
	private Map<String, PBUserResult> compareResults = new HashMap<String, PBUserResult>();

	// 每玩家游戏状态信息
	// bit 0 : is auto bet?  [0: false, 1: true]
	// bit 1 : has checked card? [0: false, 1: true]
	// bit 2 : has folded card? [0: false, 1: true]
	// bit 3 : has showed card? [0: false, 1: true]
	// bit 4 : has losed the game? [0: false, 1: true]
	// --- from bit 5 to bit 13, is to store per-user's last action
	// bit 5 : none
	// bit 6 : bet
	// bit 7 : raise bet
	// bit 8 : auto bet
	// bit 9 : check card
	// bit 10 : fold card
	// bit 11 : compare card
	// bit 12 : show card
	// bit 13 : change card
	// *** so the initial value of userInfoMask is 0x20 (0 0000 0010 0000) 
	private Map<String, Integer> userPlayInfoMask = new ConcurrentHashMap<String, Integer>();
	
	private ChangeCardResponse response = null;
	
	private static final String ROBOT_UID_PREFIX = "9999";
	
	private UserGameResultService gameResultService = UserGameResultService.getInstance();
	
	private String compareWinner = null;
	private String compareLoser = null;
	
	public DdjGameSession(int sessionId, String name, String password, boolean createByUser, String createBy, 
			int ruleType, int maxPlayerCount, int testEnable, int initSingleBet, int totalBetThreshold, int maximumAnte) {
		super(sessionId, name, password, createByUser, createBy, ruleType, maxPlayerCount, testEnable);
		// init state
		this.currentState = DdjGameStateMachineBuilder.INIT_STATE;
		// 初始化一副“新”扑克牌
		for (int i = 0; i < DdjGameConstant.ALL_CARD_NUM; i++) {
			pokerPool[i] = i;
		}
		this.singleBet = this.initSingleBet = initSingleBet;
		this.totalBetThreshold = totalBetThreshold;
		this.maximumAnte = maximumAnte;
	}
	

	public void resetGame(){
		super.resetGame();
	}
	
	
	@Override	
	public void restartGame(){	
		singleBet = initSingleBet;
		pokerPoolCursor = 0;
		someOneGotKing = false;
		userPokersMap.clear();
		cardTypeMap.clear();
		rankMaskMap.clear();
		suitMaskMap.clear();
		faceStatusMap.clear();
		totalBetMap.clear();
		userPlayInfoMask.clear();
		pairRankMap.clear();
		compareResults.clear();
		userResults.clear();
		playerBetTimes.clear();
		timeoutSettingMap.clear();
	}

	
	public int getAlivePlayerCount() {
		ServerLog.info(sessionId, "<ZjhGameSession>alivePlayerCount= "+ alivePlayerCount);
		return alivePlayerCount.get();
	}


	public int getTotalBet() {
		return totalBet;
	}


	public int getSingleBet() {
		return singleBet;
	}
	
	
	public synchronized GameResultCode bet(String userId, int givenSingleBet, int count,
			boolean isAutoBet) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.bet> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			// 更新房间当前单注
			int oldSingleBet = singleBet;
			singleBet = givenSingleBet;
					
			// 更新该玩家总注
			int tmp = totalBetMap.get(userId);
			totalBetMap.put(userId, tmp + givenSingleBet * count);
				
			// 更新房间当前总注 
			totalBet += givenSingleBet * count;
			
			// 更新该玩家下注次数
			if (playerBetTimes.containsKey(userId)) {
				int betTimes = playerBetTimes.get(userId);
				playerBetTimes.put(userId, ++betTimes);
			} else {
				playerBetTimes.put(userId,1);
			}
			
			int oldValue = userPlayInfoMask.get(userId);
			oldValue &= ~LAST_ACTION_MASK; // 先清空lastAction
			// Judge which bet type: bet(跟注), raise bet(加注), auto bet(自动跟注)
			if ( isAutoBet ) {
					// 更新lastAction, 并置状态为AUTO_BET
					userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_AUTO_BET | USER_INFO_AUTO_BET);
			} else if ( oldSingleBet < givenSingleBet ) {
					userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_RAISE_BET);
			} else { 
					userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_BET);
			}
		}
		return GameResultCode.SUCCESS;
	}


	public GameResultCode checkCard(String userId) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.checkCard> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			int userInfo = userPlayInfoMask.get(userId);
			if ( (userInfo & USER_INFO_CHECKED_CARD) == USER_INFO_CHECKED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.checkCard> "+ userId+ "has checked card !!! Needn't recheck ");
				return GameResultCode.ERROR_ALREADY_CHECK_CARD;
			} 
			if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.checkCard> "+ userId+ "has folded card !!! Can't check ");
				return GameResultCode.ERROR_ALREADY_FOLD_CARD;
			}
			else {
				int oldValue = userPlayInfoMask.get(userId);
				oldValue &= ~LAST_ACTION_MASK; // 先清空lastAction
				userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_CHECK_CARD | USER_INFO_CHECKED_CARD);
			}
		}
		return GameResultCode.SUCCESS;
	}

	
	public boolean hasUserCheckedCard(String userId) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.hasUserCheckedCard> "+ userId+ " not in this session???!!!");
			return false;
		} 
		
		int userInfo = userPlayInfoMask.get(userId);
		return ((userInfo & USER_INFO_CHECKED_CARD) == USER_INFO_CHECKED_CARD);
	}
	
	
	public synchronized GameResultCode foldCard(String userId) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.foldCard> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			int userInfo = userPlayInfoMask.get(userId);
			if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.foldCard> "+ userId+ " has folded card !!! Can not fold again");
				return GameResultCode.ERROR_ALREADY_FOLD_CARD;
			}
			else {
				int oldValue = userPlayInfoMask.get(userId);
				oldValue &= ~LAST_ACTION_MASK; // 先清空lastAction
				userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_FOLD_CARD | USER_INFO_FOLDED_CARD);
				
				// 递减存活玩家个数, 并设玩家游戏状态为loseGame为true
				alivePlayerCount.decrementAndGet(); // he/she is game over
				
				// 把玩家loseGame状态设为true，以免在selectPlayUser时再被选择到(弃牌后该玩家游戏结束)。
				GameUser user = findUser(userId); // 不能用GameUserManager.getInstance().findUserById()这个方法
				if ( user != null ) {
					user.setLoseGame(true);
				}
				
				// 弃牌后表示该玩家已经输，扣除其金币, 更新游戏次数数据
				boolean win = false;
				PBUserResult result = gameResultService.makePBUserResult(userId, win, -1 * totalBetMap.get(userId));
				gameResultService.writeUserCoinsIntoDB(sessionId, result, DBConstants.C_CHARGE_SOURCE_ZJH_FOLD_CARD);
				gameResultService.writeUserGameResultIndoDb(sessionId, result, DdjGameConstant.GAME_ID_ZJH);
				
				// 放入以便结束时一并传回
//				userResults.put(userId, result);
				
				ServerLog.info(sessionId, "After "+userId+" folding card, alivePlayerCount= " + alivePlayerCount);
			}
		}
		return GameResultCode.SUCCESS;
	}


	public GameResultCode showCard(String userId, List<Integer> pokerIds) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.showCard> "+ userId+ " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			int userInfo = userPlayInfoMask.get(userId);
			if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.showCard> "+ userId+ "has folded card !!! Can't show card ");
				return GameResultCode.ERROR_ALREADY_FOLD_CARD;
			}
			// 亮牌.
			long faceStatusMask = faceStatusMap.get(userId);
			for ( Integer showPokerId : pokerIds ) {
				faceStatusMask |= (1 << showPokerId); // 把对应位置为1, 表示该牌亮出来了
			}
			faceStatusMap.put(userId, faceStatusMask);
			
			// 更新其他状态，包括lastAction等
			int oldValue = userPlayInfoMask.get(userId);
			oldValue &= ~LAST_ACTION_MASK; // 先清空lastAction
			userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_SHOW_CARD | USER_INFO_SHOWED_CARD);
		}
		return GameResultCode.SUCCESS;
	}


	public GameResultCode compareCard(final String userId, String toUserId) {
		
		if ( !userPlayInfoMask.containsKey(userId) ||  !userPlayInfoMask.containsKey(toUserId)) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.compareCard> "+ userId+ " or "+ toUserId +" not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		
		int userInfo = userPlayInfoMask.get(userId);
		int toUserInfo = userPlayInfoMask.get(toUserId);
		int combinedUserInfo = userInfo | toUserInfo;
		if ( (combinedUserInfo & USER_INFO_COMPARE_LOSE) == USER_INFO_COMPARE_LOSE 
				|| (combinedUserInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.compareCard> "+ userId+ "or "+
				toUserId+" has foled card or losed the game, fail to compare card !!!");
			return GameResultCode.ERROR_CANNOT_COMPARE_CARD;			
		}
		
		// 开始比牌！
		doCompareCard(userId, toUserId);
		
		// 如果发起比牌者(ID为userId的玩家)输了,要额外惩罚,扣除一定金币： 该场最大筹码数×4
		int compareChanllengerLoss = 0;
		if ( userId.equals(compareLoser) && ! userId.startsWith(ROBOT_UID_PREFIX)) {
			compareChanllengerLoss = maximumAnte * DdjGameConstant.COMPARE_CHANLLEGER_LOSS_MULTIPLY_FACTOR;
			dbService.executeDBRequest(sessionId, new Runnable() {
				@Override
				public void run() {
					MongoDBClient dbClient = dbService.getMongoDBClient(sessionId);
					UserManager.deductAccount(dbClient, userId, maximumAnte * DdjGameConstant.COMPARE_CHANLLEGER_LOSS_MULTIPLY_FACTOR, DBConstants.C_CHARGE_SOURCE_ZJH_COMPARE_LOSE);
				}
			});
			ServerLog.info(sessionId, "<compareCard> User " + userId + " chanllenges to compare card, but fails, so gets " +
					compareChanllengerLoss + " coins loss. ");
		}
				
		// 刷新winner的状态
		int winnerOldInfo = userPlayInfoMask.get(compareWinner);
		winnerOldInfo &= ~LAST_ACTION_MASK; // 先清空lastAction
		userPlayInfoMask.put(compareWinner, winnerOldInfo | USER_INFO_ACTION_COMPARE_CARD );
		
		// 刷新loser的状态
		int loserOldInfo = userPlayInfoMask.get(compareLoser);
		loserOldInfo &= ~LAST_ACTION_MASK; // 先清空lastAction
		userPlayInfoMask.put(compareLoser, loserOldInfo | USER_INFO_ACTION_COMPARE_CARD | USER_INFO_COMPARE_LOSE );
		alivePlayerCount.decrementAndGet(); //只要副作用，返回值不要
		GameUser loserUser = findUser(compareLoser);// 不能用GameUserManager.getInstance().findUserById()这个方法
		ServerLog.info(sessionId, "<ZjhGameSession.compareCard> loserUser = " + (loserUser == null ? null : loserUser) );
		if ( loserUser != null ) {
			ServerLog.info(sessionId, "<ZjhGameSession.compareCard> Set " + userId + " loseGame status to true.");
			loserUser.setLoseGame(true); //设玩家游戏状态为loseGame为true
		}
		
		// 并马上扣除其赌注
		PBUserResult result = gameResultService.makePBUserResult(compareLoser, false, -1 * totalBetMap.get(compareLoser));
		gameResultService.writeUserCoinsIntoDB(sessionId, result, DBConstants.C_CHARGE_SOURCE_ZJH_COMPARE_LOSE);
		
		// 构造userResult以返回
		compareResults.clear(); // 之前可能有人比牌,所以要清除
		
		PBUserResult winnerResult = gameResultService.makePBUserResult(compareWinner, true, 0);
		compareResults.put(compareWinner, winnerResult);
		
		PBUserResult loserResult = gameResultService.makePBUserResult(compareLoser,false,-1 * compareChanllengerLoss);
		compareResults.put(compareLoser, loserResult);
		 
		// 放入最终结果,以便游戏结束时一起送回客户端
//		userResults.put(loser, loserResult);
		
		return GameResultCode.SUCCESS;
	}


	private void doCompareCard(final String userId, String toUserId) {
		
		compareWinner = null;
		compareLoser = null;
		
		int userCardType = cardTypeMap.get(userId).ordinal();
		int toUserCardType = cardTypeMap.get(toUserId).ordinal();
		ServerLog.info(sessionId, "userCardType = " + PBZJHCardType.valueOf(userCardType)
				+ ", toUserCardType = " + PBZJHCardType.valueOf(toUserCardType));
			
		// 特殊情况, 玩家牌是2,3 5, 但当前局没人拿到豹子牌，那就只能认为是散牌
		if ( userCardType == SPECIAL_VALUE && !someOneGotKing) {
				userCardType = HIGH_CARD_VALUE; 
		}
		if (toUserCardType == SPECIAL_VALUE && !someOneGotKing) {
				toUserCardType = HIGH_CARD_VALUE;
		}
		
		// 一般情况, 开始具体比牌操作
		if ( userCardType < toUserCardType ) {
				compareWinner = toUserId;
				compareLoser = userId;
		}
		else if ( userCardType > toUserCardType ) {
				compareWinner = userId;
				compareLoser = toUserId;
		}
		else {
			// 牌型一样，比牌面值. 顺金，金花，顺子都直接从大比到小，对子则先比对子的大小
			int userRankMask = rankMaskMap.get(userId);
			int toUserRankMask = rankMaskMap.get(toUserId);
			if ( userCardType == PAIR_VALUE ) {
				int userPairRank = pairRankMap.get(userId);
				int toUserPairRank = pairRankMap.get(toUserId);
				if ( userPairRank > toUserPairRank ) {
					compareWinner = userId;
					compareLoser = toUserId;
				} else if (userPairRank < toUserPairRank ) {
					compareWinner = toUserId;
					compareLoser = userId;
				}  else {
					// 先把对子牌的掩码位清除（置为1）
					userRankMask |= 1 << (userPairRank - 2 );
					toUserRankMask |= 1 << (toUserPairRank - 2 );
					// 剩下的值就是单张牌的掩码值，直接比较
					if ( userRankMask > toUserRankMask ) {
						compareWinner = toUserId;
						compareLoser = userId;
					} else if ( userRankMask < toUserRankMask ) {
						compareWinner = userId;
						compareLoser = toUserId;
					} else {
						// 牌面一样大，直接判发起比牌的玩家输！
						compareWinner = toUserId;
						compareLoser = userId;
					}
				}
			}
			// A23顺子牌特殊考虑   
			else if ( userRankMask == RANK_MASK_A23 && toUserRankMask != RANK_MASK_A23) {
				compareWinner = toUserId;
				compareLoser = userId;
			}
			else if ( userRankMask != RANK_MASK_A23 && toUserRankMask == RANK_MASK_A23) {
				compareWinner = userId;
				compareLoser = toUserId;
			}
			else if ( userRankMask == RANK_MASK_A23 && toUserRankMask == RANK_MASK_A23) {
				compareWinner = toUserId;
				compareLoser = userId;
			}
			else if ( userRankMask < toUserRankMask ) {
				compareWinner = userId;
				compareLoser = toUserId;
			} 
			else if ( userRankMask > toUserRankMask ) {
				compareWinner = toUserId;
				compareLoser = userId;
			} 
			else {
				// 如果牌面值也是一样大，不再进行花色的比较，直接判定主动比牌的玩家输！
				compareWinner = toUserId;
				compareLoser = userId;
			}
		}
	}

	
	// 开局，发送StartGameNotification时调用此方法
	public List<PBZJHUserPlayInfo> deal() {
			
			List<PBZJHUserPlayInfo> result = new ArrayList<PBZJHUserPlayInfo>();
			List<GameUser> gameUsers = getUserList().getPlayingUserList();
			
	 		// 先洗牌 !
			shufflePokers();
			 
			// 给每个玩家发牌，并构造消息以便返回
			for(GameUser user : gameUsers) {
				String userId = user.getUserId();
				List<PBPoker> pokers = dispatchPokers();
				// 按牌面值大小排序，以方便后续比牌操作, 存起备用
				Collections.sort(pokers, new Comparator<PBPoker>() {
					@Override
					public int compare(PBPoker p1, PBPoker p2) {
						int p1Val = p1.getRank().getNumber();
						int p2Val = p2.getRank().getNumber();
									
						return p2Val - p1Val;  // 降序，所以用p2-p1 !
					}
				});	
				userPokersMap.put(userId, pokers);

				//检查牌类型，存起备用
				PBZJHCardType cardType = introspectCardType(pokers,userId);
				cardTypeMap.put(userId, cardType);
//				ServerLog.info(sessionId, "<deal> User "+userId+" gets pokers: \n" + pokers.toString()
//						+",\n cardType is " + cardType);
				
				// 一开局，每个玩家的总注等于当前房间单注
				totalBetMap.put(userId, singleBet);

				// 初始化每个玩家的playInfo值，非常重要的数据结构
				userPlayInfoMask.put(userId, DdjGameConstant.USER_INFO_INITIAL_VALUE);
				
				result.add(updateUserPlayInfo(userId));
			}
			
			return result;
		}


		private void shufflePokers() {

			// 洗牌！
			// 前(ALL_CARD_NUM - 1)张中随机一张跟最后一张交换，然后，前(ALL_CARD_NUM - 2)张中随机一张跟倒数第二张
			// 交换，etc.这样，就可以把牌洗得很乱。
			for (int i = DdjGameConstant.ALL_CARD_NUM - 1; i > 0; i--) {
				int random = RandomUtils.nextInt(i);
				int tmp = pokerPool[random];
				pokerPool[random] = pokerPool[i];
				pokerPool[i] = tmp;
			}
		}
		
		
		private synchronized List<PBPoker> dispatchPokers() {
			
			List<PBPoker> result = new ArrayList<PBPoker>();
			PBPoker pbPoker = null;
			
			for (int i = 0; i < DdjGameConstant.PER_USER_CARD_NUM; i++) {
				pbPoker = getOneCardFromPokerPool();
				result.add(pbPoker);		
			}
			
			return result;
		}

		
		// 该方法会在发牌时和换牌时被调用。
		private PBZJHCardType introspectCardType(List<PBPoker> pokers,String userId) {
			
			PBZJHCardType type = null;
			int[] ranks = new int[DdjGameConstant.PER_USER_CARD_NUM];
			int[] suits = new int[DdjGameConstant.PER_USER_CARD_NUM];
			
			int rankMask = DdjGameConstant.RANK_MASK;
			int suitMask = DdjGameConstant.SUIT_MASK;
			long faceStatusMask = DdjGameConstant.FACE_STATUS_MASK;
			boolean foundPair = false;
			
			//如果已经存有该玩家的状态，要清空; 没有的话, 此操作也无副作用。
			rankMaskMap.remove(userId);
			suitMaskMap.remove(userId);
			faceStatusMap.remove(userId);
			pairRankMap.remove(userId);
			cardTypeMap.remove(userId);
			
			for (int i = 0; i < DdjGameConstant.PER_USER_CARD_NUM; i++) {
				PBPoker poker = pokers.get(i);
				if ( poker == null ) {
					ServerLog.error(sessionId, new NullPointerException());
					return PBZJHCardType.UNKNOW;
				}
				// 获得序号值来作为牌面在掩码中的第几位，比如A的ordinal是12, 即对应
				// 掩码中的第12位（从0开始计数）; 花色亦同。
				// 牌面： 1 1111 1111 1111， 从左到右分别对应: A KQJ10 9876 5432
				// 花色： 1111， 从左到右分别对应： 黑桃，红心，梅花，方块
				ranks[i] = poker.getRank().ordinal();
				suits[i] = poker.getSuit().ordinal();
				
				// 根据ordinal值把掩码中对应位置为0
				rankMask &= ~( 1 << ranks[i]);
				suitMask &= ~( 1 << suits[i]);
				// faceStatusMask是一个表示52张牌是否亮出的值，当亮出时,即把对应的pokerId位
				// 置为1.
				faceStatusMask &= ~( 1 << toPokerId(PBPokerRank.valueOf(ranks[i]+2), 
						PBPokerSuit.valueOf(suits[i]+1))); 
				
				// 记录下对子牌
				if ( i > 0 && !foundPair) {
					for ( int j = 0; j < i ; j++ ){
						if ( ranks[j] == ranks[i]) { 
							pairRankMap.put(userId, ranks[i]+2);
							foundPair = true;
							break;
						}
					}
				}
			}
				
			// 以备后用
			rankMaskMap.put(userId, rankMask);
			suitMaskMap.put(userId, suitMask);
			faceStatusMap.put(userId, faceStatusMask);
			
			// 有几种牌面值, 表示为牌面掩码有几个0。0x1FFF表示只考虑rankMask的低13位，即AKQJ,10-2, 共13种牌面值。
			int howManyRanks = IntegerUtil.howManyBits(rankMask, 0x1FFF, 0);
			// 有几种花色花色, 表示为花色掩码有几个0。0xF表示只考虑suitMask低4位，即四种花色。
			int howManySuits = IntegerUtil.howManyBits(suitMask,0xF, 0);
			// 牌面值掩码有没有连续的3个0, 有的话表示是顺子牌。0x1FFF意义同前。 
			boolean hasThreeConsecutiveBit = IntegerUtil.hasConsecutiveBit(rankMask, 0x1FFF,  3, 0);
			
			if ( rankMask == DdjGameConstant.TYPE_SPECIAL && howManySuits > 1) { 
				type = PBZJHCardType.SPECIAL; // 特殊牌：不同花色的2,3,5， 掩码值为 1 1111 1111 0100
			}
			else if ( howManyRanks == 1 ) {
				type = PBZJHCardType.THREE_OF_A_KIND; // 豹子，同一种牌面值
				someOneGotKing = true; 
			}
			else if ( howManySuits == 1) {
				if ( hasThreeConsecutiveBit || rankMask == DdjGameConstant.RANK_MASK_A23 ) {
					type = PBZJHCardType.STRAIGHT_FLUSH; // 顺金， 只有一种花色
				} else {
					type = PBZJHCardType.FLUSH; // 金花，即同花，只有一种花色
				}
			}
			else if ( hasThreeConsecutiveBit || rankMask == DdjGameConstant.RANK_MASK_A23 ) {
				type = PBZJHCardType.STRAIGHT; // 顺子，不只一种花色
			}
			else if ( howManyRanks == 2 ) {
				type = PBZJHCardType.PAIR;  // 对子， 有二种牌面值
			}
			else if ( howManyRanks == 3 && ! hasThreeConsecutiveBit) {
				type = PBZJHCardType.HIGH_CARD; // 散牌， 有三种牌面值，且不是连续的
			} 
			else {
				type = PBZJHCardType.UNKNOW; // 未知
			}
			
			return type;
		}


		public GameResultCode changeCard(String userId, int toChangeCardId) {
			
			if ( !userPlayInfoMask.containsKey(userId) ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.changeCard> "+ userId+ " not in this session???!!!");
				return GameResultCode.ERROR_USER_NOT_IN_SESSION;
			} 
			else {
				int userInfo = userPlayInfoMask.get(userId);
				if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD ) {
					ServerLog.info(sessionId, "<ZjhGameSessuion.changeCard> "+ userId+ "has folded card !!! Can't check ");
					return GameResultCode.ERROR_ALREADY_FOLD_CARD;
				}
				
				List<PBPoker> pokers = userPokersMap.get(userId);
				
				PBPoker newPoker = getOneCardFromPokerPool();
				PBPoker object = null;
				for ( PBPoker poker : pokers ) {
					if ( poker.getPokerId() == toChangeCardId ) {
						object = poker;
						break;
					}
				}
				pokers.remove(object);
				pokers.add(newPoker);
				userPokersMap.put(userId, pokers);
				
				PBZJHCardType newCardType = introspectCardType(pokers, userId);
				cardTypeMap.put(userId, newCardType);

				int oldValue = userPlayInfoMask.get(userId);
				oldValue &= ~LAST_ACTION_MASK; // 先清空lastAction
				userPlayInfoMask.put(userId, oldValue | USER_INFO_ACTION_CHANGE_CARD);
				
				response = ChangeCardResponse.newBuilder()
											.setOldCardId(toChangeCardId)
											.setNewPoker(newPoker)
											.setCardType(newCardType)
											.build();
				
				return GameResultCode.SUCCESS;
			}
		}


		private int toPokerId(PBPokerRank rank, PBPokerSuit suit) {
			
			// id start from 0
			int id = 0;
			int rankVal = rank.getNumber();
			int suitVal = suit.getNumber();
			
			id = (rankVal-2) * DdjGameConstant.SUIT_TYPE_NUM + (suitVal-1);
			
			return id;
		}


		private PBPokerRank pokerIdToRank(int pokerId) {
			
			// see protocol buffer definition for why adding 2
			int value = pokerId / DdjGameConstant.SUIT_TYPE_NUM + 2;
			
			return PBPokerRank.valueOf(value);
		}

		
		private PBPokerSuit pokerIdToSuit(int pokerId) {
			
			// see protocol buffer definition for why adding 1
			int value = pokerId % DdjGameConstant.SUIT_TYPE_NUM + 1;
			
			return PBPokerSuit.valueOf(value);
		}
		
	public PBPoker pokerIdToPBPoker(int pokerId) {
			
			PBPokerRank rank = pokerIdToRank(pokerId);
			PBPokerSuit suit = pokerIdToSuit(pokerId);
			
			PBPoker pbPoker = PBPoker.newBuilder()
										.setPokerId(pokerId)
										.setRank(rank)
										.setSuit(suit)
										.build();
			return pbPoker;
		}
		

	public GameResultCode setTimeoutAction(String userId, PBZJHUserAction action) {
		
		if ( !userPlayInfoMask.containsKey(userId) ) {
			ServerLog.info(sessionId, "<ZjhGameSessuion.setTimeoutAction> "+ userId + " not in this session???!!!");
			return GameResultCode.ERROR_USER_NOT_IN_SESSION;
		} 
		else {
			int userInfo = userPlayInfoMask.get(userId);
			if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.setTimeoutAction> "+ userId + " has folded card !!! Needn't set timeout action.");
				return GameResultCode.ERROR_ALREADY_FOLD_CARD;
			} 
			if ( (userInfo & USER_INFO_FOLDED_CARD) == USER_INFO_COMPARE_LOSE ) {
				ServerLog.info(sessionId, "<ZjhGameSessuion.setTimeoutAction> "+ userId + " has losed the game !!! Needn't set timeout action.");
				return GameResultCode.ERROR_ALREADY_FOLD_CARD;
			}
			else {
				ServerLog.info(sessionId, "<ZjhGameSessuion.setTimeoutAction> "+ userId + " set timeout action to " + action);
				timeoutSettingMap.put(userId, action);
			}
		}
		
		return GameResultCode.SUCCESS;
	}

	
	// 游戏中途玩家加入时，调用这个方法更新userPlayInfoList,以传给客户端
	public List<PBZJHUserPlayInfo> getUserPlayInfo() {
		
		ServerLog.info(sessionId, "<getUserPlayInfo> someone joins in during game!");
		
		List<PBZJHUserPlayInfo> result =  new ArrayList<PBZJHUserPlayInfo>();
		List<GameUser> gameUsers = getUserList().getPlayingUserList();
		
		for(GameUser user : gameUsers) {
			result.add( updateUserPlayInfo(user.getUserId()) );
		}
		
		return result;
	}

	
	// 一开局， 以及游戏中途玩家加入时，都会调用这个方法
	public PBZJHUserPlayInfo updateUserPlayInfo(String userId) {
		
		List<PBPoker> pbPokers = new ArrayList<PBPoker>();
			
		List<PBPoker> pbPokerList =  userPokersMap.get(userId);
		long faceStatus = faceStatusMap.get(userId);
		for ( PBPoker pbPoker : pbPokerList ) {
			PBPokerRank rank = pbPoker.getRank();
			PBPokerSuit suit = pbPoker.getSuit();
			int pokerId = toPokerId(rank, suit);
			// faceStatus是亮牌掩码， 检查对应位是否为1, 是表示亮牌，否则表示没亮牌
			boolean faceUp = IntegerUtil.testBit(faceStatus, pokerId, 1);
				
			PBPoker newPBPoker = PBPoker.newBuilder()
							.setPokerId(pokerId)
							.setRank(rank)
							.setSuit(suit)
							.setFaceUp(faceUp)
							.build();
			pbPokers.add(newPBPoker);
		}
			
		PBZJHPoker zjhPoker = PBZJHPoker.newBuilder()
						.addAllPokers(pbPokers)
						.setCardType(cardTypeMap.get(userId))
						.build();
			
		int userPlayInfo = userPlayInfoMask.get(userId);
		int totalBet = totalBetMap.get(userId);
		PBZJHUserAction lastAction = lastAction(userPlayInfo);
		boolean isAutoBet = 
			((userPlayInfo & USER_INFO_AUTO_BET) == USER_INFO_AUTO_BET? true:false);
		boolean hasCheckedCard = 
			((userPlayInfo & USER_INFO_CHECKED_CARD) == USER_INFO_CHECKED_CARD? true:false);
		boolean hasFoldedCard = 
			((userPlayInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD? true:false);
		boolean hasShowedCard =
			((userPlayInfo & USER_INFO_SHOWED_CARD) == USER_INFO_SHOWED_CARD? true:false);
		boolean compareLosed = 
			((userPlayInfo & USER_INFO_COMPARE_LOSE) == USER_INFO_COMPARE_LOSE? true:false);
			
		PBZJHUserPlayInfo pbZjhUserPlayInfo = PBZJHUserPlayInfo.newBuilder()
				.setUserId(userId)
				.setPokers(zjhPoker)
				.setTotalBet(totalBet)
				.setIsAutoBet(isAutoBet)
				.setLastAction(lastAction)
				.setAlreadCheckCard(hasCheckedCard)
				.setAlreadFoldCard(hasFoldedCard)
				.setAlreadShowCard(hasShowedCard)
			   .setAlreadCompareLose(compareLosed)
				.build();
		
//		ServerLog.info(sessionId, " PBZJHUserPlayInfo for " + userId + " is : " + pbZjhUserPlayInfo.toString());
		
		return pbZjhUserPlayInfo;
	}

	
	private PBZJHUserAction lastAction(int userPlayInfoMask) {
		
		int lastAction = userPlayInfoMask & LAST_ACTION_MASK;
		switch (lastAction) {
			case USER_INFO_ACTION_BET:
				return PBZJHUserAction.BET;
			case USER_INFO_ACTION_RAISE_BET:
				return PBZJHUserAction.RAISE_BET;
			case USER_INFO_ACTION_AUTO_BET:
				return PBZJHUserAction.AUTO_BET;
			case USER_INFO_ACTION_CHECK_CARD:
				return PBZJHUserAction.CHECK_CARD;
			case USER_INFO_ACTION_FOLD_CARD:
				return PBZJHUserAction.FOLD_CARD;
			case USER_INFO_ACTION_COMPARE_CARD:
				return PBZJHUserAction.COMPARE_CARD;
			case USER_INFO_ACTION_SHOW_CARD:
				return PBZJHUserAction.SHOW_CARD;
			case USER_INFO_ACTION_CHANGE_CARD:
				return PBZJHUserAction.CHANGE_CARD;
			default:
				return PBZJHUserAction.NONE;
		}
	}
	
	
	public void setAlivePlayerCount(int playUserCount) {
		alivePlayerCount.set(playUserCount);
	}


	
	public void setTotalBet(int playUserCount) {
		totalBet = singleBet * playUserCount;
	}

	
	public PBUserResult judgeWhoWins() {
	
		PBUserResult result = null; 
		PBUserResult nominalResult = null;
		
		/**
		 * 判定赢家
		 */
		compareWinner = null;
		synchronized (userPlayInfoMask) {
			for ( Map.Entry<String, Integer> entry : userPlayInfoMask.entrySet()) {
				int userPlayInfo = entry.getValue();
	            // 当真正开始玩（ACTUAL_PLAYING, 即进入轮次变更时）并且玩家未弃牌并且比牌未输，算是胜者;
				if (status.equals(SessionStatus.ACTUAL_PLAYING) 
						&& ( userPlayInfo & USER_INFO_COMPARE_LOSE) == USER_INFO_COMPARE_LOSE 
                  || ( userPlayInfo & USER_INFO_FOLDED_CARD) == USER_INFO_FOLDED_CARD )
				{   // 跳过已经比输或弃牌的玩家
					continue;
				}
				if ( compareWinner == null) {
					compareWinner = entry.getKey();
					continue;
				}
				
				doCompareCard(compareWinner, entry.getKey());
				// 并马上扣除其赌注, doCompareCard后, compareWinner存着胜者，compareLoser存着败者
				PBUserResult loserResult = gameResultService.makePBUserResult(compareLoser, false, -1 * totalBetMap.get(compareLoser));
				gameResultService.writeUserCoinsIntoDB(sessionId, loserResult, DBConstants.C_CHARGE_SOURCE_ZJH_COMPARE_LOSE);
			}
		}
		
		if ( compareWinner != null ) {
			/** 
			 * 扣赢家的税
			 */
			int tax = (int)Math.round(totalBet*DdjGameConstant.WINNER_TAX_RATE);
			ServerLog.info(sessionId, "<judgeWhoWins> Tax the winner 10 percent of his gain coins. Tax is : "+ totalBet
				+" * "+ DdjGameConstant.WINNER_TAX_RATE + " = " + tax);

			/**
			 *  构造结果
			 */
			int gainCoins = totalBet - totalBetMap.get(compareWinner) - tax; // 自己下的赌注只是帐面上的值，并没有真正掏出口袋，
																					 // 所以最后收获的值需要减去自己的赌注
			result = gameResultService.makePBUserResult(compareWinner, true, gainCoins);
			// 名义上的结果, 需要把赢家自己的赌注也加上, 以返回给客户端
			nominalResult = gameResultService.makePBUserResult(compareWinner, true, totalBet - tax);
			userResults.put(compareWinner, nominalResult);
		}
		
		return result;
	}


	// 当玩家中途退出时（指未完成游戏），把其游戏状态设为loseGame，把其isplaying设为false
	public void updateQuitPlayerInfo(String userId) {
		
		if ( !isGamePlaying() ) {
			return;
		}
	
		// 如果是游戏玩家并且游戏状态为未输，需要把alivePlayerCount递减；旁观玩家则不需要
		// 如果已经是输了游戏的也不需要递减（因为之前已经递减了）
		GameUser gameUser = findUser(userId); // 不能用GameUserManager.getInstance().findUserById()这个方法
		if ( gameUser != null && gameUser.isPlaying() && ! gameUser.hasLosedGame() ) {
				alivePlayerCount.decrementAndGet();
		}
		
		// 设其isplaying为false
		if ( gameUser != null ) {
			gameUser.setPlaying(false);
		}
		
		
		if ( userPlayInfoMask.containsKey(userId)) {
			int playInfoMask = userPlayInfoMask.get(userId);
			// 中途退出当做是输了游戏, 权且把其设为COMPARE_LOSE
			userPlayInfoMask.put(userId, playInfoMask | USER_INFO_COMPARE_LOSE ); 
			
			// 已经输了的，或者已经扣牌的，赌注已经被扣了，不须要再多扣一次！
			if ( (playInfoMask & USER_INFO_COMPARE_LOSE) != USER_INFO_COMPARE_LOSE 
					|| (playInfoMask & USER_INFO_FOLDED_CARD) != USER_INFO_FOLDED_CARD) { 
				PBUserResult result = gameResultService.makePBUserResult(userId, false, -1 * totalBetMap.get(userId));
				gameResultService.writeUserCoinsIntoDB(sessionId, result, DBConstants.C_CHARGE_SOURCE_ZJH_QUIT_GAME);
				gameResultService.writeUserGameResultIndoDb(sessionId, result, DdjGameConstant.GAME_ID_ZJH);
			}
			userPlayInfoMask.remove(userId);
		}
		
	}
	
	
	private PBPoker getOneCardFromPokerPool() {
		
		PBPokerRank rank = null;
		PBPokerSuit suit = null;
		
		synchronized (this) {
			rank = PBPokerRank.valueOf(pokerPool[pokerPoolCursor] / SUIT_TYPE_NUM + 2);
			suit = PBPokerSuit.valueOf(pokerPool[pokerPoolCursor] % SUIT_TYPE_NUM + 1);
			pokerPoolCursor++; // 扑克堆游标，游标前面的表示已经发出的牌, 每发出一张牌就把游标前移一个元素。
		}
		
		int pokerId = toPokerId(rank, suit);
		boolean faceUp = false;
		PBPoker newPoker = PBPoker.newBuilder()
							.setPokerId(pokerId)
							.setRank(rank)
							.setSuit(suit)
							.setFaceUp(faceUp)
							.build();
		
		return newPoker;
	}
	
	
	public List<String> getComprableUserIdList(String myselfId) {
		
		List<String> result = new ArrayList<String>();
		
		for ( Map.Entry<String, Integer>  entry: userPlayInfoMask.entrySet()) {
			String userId = entry.getKey();
			if ( ! userId.equals(myselfId) && (entry.getValue() & USER_INFO_COMPARE_LOSE) != USER_INFO_COMPARE_LOSE
					&& (entry.getValue() & USER_INFO_FOLDED_CARD) != USER_INFO_FOLDED_CARD) {
				result.add(userId);
			}
		}
		return result;
	}


	public Collection<PBUserResult> getCompareResults() {
		return Collections.unmodifiableCollection(compareResults.values());
	}

	
	public synchronized ChangeCardResponse getChangeCardResponse() {
		ChangeCardResponse result = response;
		response = null;
		return result;
	}

	public PBZJHUserAction chooseCurrentPlayerTimeoutAction() {

		String currentPlayerId = getCurrentPlayUserId();
		if ( currentPlayerId == null ) {
			ServerLog.info(sessionId, "<chooseCurrentPlayerTimeoutAction> current player Id is null ?! ");
			return null;
		}
		
		PBZJHUserAction timeoutAction = timeoutSettingMap.get(currentPlayerId);
		ServerLog.info(sessionId, "<chooseCurrentPlayerTimeoutAction> current player "+ currentPlayerId + " chooses " + timeoutAction);
		return timeoutAction;
	}


	// 达到房间总注限额就会结束游戏
	public boolean shallCompleteGame() {
		return totalBet >= totalBetThreshold;
	}
	
}