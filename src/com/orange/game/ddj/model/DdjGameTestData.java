package com.orange.game.ddj.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.math.RandomUtils;

import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPoker;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerRank;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBPokerSuit;
import com.orange.network.game.protocol.model.ZhaJinHuaProtos.PBZJHCardType;

public class DdjGameTestData {

	private DdjGameTestData() {
	}
	
	final static DdjGameTestData test = new DdjGameTestData();
	
	public static DdjGameTestData getInstance() {
		return test;
	}
	
	
	private int[] pokerPool = new int[DdjGameConstant.ALL_CARD_NUM];
	private Map<Integer, Boolean> pokerRankMap = new HashMap<Integer, Boolean>();
	
	// 初始化区块, constructor自动调用运行
	{
		for ( int i = 0; i < DdjGameConstant.ALL_CARD_NUM; i++ ) {
			pokerPool[i] = i;
		}
		for ( int i = 0; i < DdjGameConstant.PER_SUIT_NUM; i++ ) {
			pokerRankMap.put(i+2, false);
		}
	}
	
	List<PBPoker> dispatchPokersForTest(PBZJHCardType cardType) {
		switch (cardType.getNumber()) {
			case PBZJHCardType.SPECIAL_VALUE:
				return specialPokers();
			case PBZJHCardType.THREE_OF_A_KIND_VALUE:
				return threeOfAKindPokers();
			case PBZJHCardType.STRAIGHT_FLUSH_VALUE:
				return straightFlushPokers();
			case PBZJHCardType.FLUSH_VALUE:
				return straightFlushPokers();
			case PBZJHCardType.STRAIGHT_VALUE:
				return straightPokers();
			case PBZJHCardType.PAIR_VALUE:
				return pairPokers();
			default:
				return null;
		}
		
	}

	
	private List<PBPoker> straightPokers() {
		// TODO Auto-generated method stub
		return null;
	}

	private List<PBPoker> straightFlushPokers() {
		// TODO Auto-generated method stub
		return null;
	}

	private List<PBPoker> threeOfAKindPokers() {
		
		List<PBPoker> result = new ArrayList<PBPoker>();
		PBPokerRank rank = null;
		PBPokerSuit suit = null;
		PBPoker pbPoker = null;
		int pokerId;
		boolean faceUp = false;
		boolean sameSuit;
		
		int random = RandomUtils.nextInt(DdjGameConstant.PER_SUIT_NUM)+2;
		for (int i = 0; i <DdjGameConstant.PER_USER_CARD_NUM; i++) {
			rank = PBPokerRank.valueOf(random);
			suit = PBPokerSuit.values()[RandomUtils.nextInt(4)];
			pokerId = toPokerId(rank, suit); 		
			
			pbPoker = PBPoker.newBuilder()
					.setPokerId(pokerId)
					.setRank(rank)
					.setSuit(suit)
					.setFaceUp(faceUp)
					.build();
			result.add(pbPoker);		
		}
		return  result;
	}

	private List<PBPoker> pairPokers() {
		
		int random = RandomUtils.nextInt(DdjGameConstant.PER_SUIT_NUM);
		
		List<PBPoker> result = new ArrayList<PBPoker>();
		PBPokerRank rank = null;
		PBPokerSuit suit = null;
		PBPoker pbPoker = null;
		int pokerId;
		boolean faceUp = false;
		
		for (int i = 0; i < 2; i++) {
			rank = PBPokerRank.valueOf(random + 2);
			suit = PBPokerSuit.values()[RandomUtils.nextInt(4)];
			pokerId = toPokerId(rank, suit); 		
			
			pbPoker = PBPoker.newBuilder()
					.setPokerId(pokerId)
					.setRank(rank)
					.setSuit(suit)
					.setFaceUp(faceUp)
					.build();
			result.add(pbPoker);		
		}
		// 散牌
		int value;
		while ( (value = RandomUtils.nextInt(DdjGameConstant.PER_SUIT_NUM)) != random ){
			rank = PBPokerRank.valueOf(value+2);
			suit = PBPokerSuit.values()[RandomUtils.nextInt(4)];
			pokerId = toPokerId(rank, suit); 		
			
			pbPoker = PBPoker.newBuilder()
					.setPokerId(pokerId)
					.setRank(rank)
					.setSuit(suit)
					.setFaceUp(faceUp)
					.build();
			result.add(pbPoker);
			break;
		}
		return  result;
	}

	private List<PBPoker> specialPokers() {
		
		PBPokerRank[] specialRank = { PBPokerRank.POKER_RANK_2,
				PBPokerRank.POKER_RANK_3, PBPokerRank.POKER_RANK_5 };
		
		List<PBPoker> result = new ArrayList<PBPoker>();
		PBPokerRank rank = null;
		PBPokerSuit suit = null;
		PBPoker pbPoker = null;
		int pokerId;
		boolean faceUp = false;
		boolean sameSuit;
		
		int suitNum = 0;
		
		if ( sameSuit = RandomUtils.nextBoolean() ) {
			suitNum = RandomUtils.nextInt(4);
		}

		for (int i = 0; i <DdjGameConstant.PER_USER_CARD_NUM; i++) {
			rank = specialRank[i];
			suit = PBPokerSuit.values()[sameSuit == true? suitNum : RandomUtils.nextInt(4)];
			pokerId = toPokerId(rank, suit); 		
			
			pbPoker = PBPoker.newBuilder()
					.setPokerId(pokerId)
					.setRank(rank)
					.setSuit(suit)
					.setFaceUp(faceUp)
					.build();
			result.add(pbPoker);		
		}
		return  result;
	}
	
	
	private int toPokerId(PBPokerRank rank, PBPokerSuit suit) {
		
		// id start from 0
		int id = 0;
		int rankVal = rank.getNumber();
		int suitVal = suit.getNumber();
		
		id = (rankVal-2) * DdjGameConstant.SUIT_TYPE_NUM + (suitVal-1);
		
		return id;
	}
}
