
package com.orange.game.ddj.model;

import com.orange.game.constants.DBConstants;


public class DdjGameConstant {
	
	public static final String GAME_ID_ZJH = DBConstants.GAME_ID_ZHAJINHUA;
	
	public static final int MAX_PLAYER_PER_SESSION = 5;
	
	public static final int PER_USER_CARD_NUM = 3; // each user has 3 cards;
	public static final int ALL_CARD_NUM = 52;
	public static final int PER_SUIT_NUM = 13; // 2-10 ,J, Q ,K
	public static final int SUIT_TYPE_NUM = 4; // spade, heart, club, diamond
	
	// For card type
	
	// Binary: 11111111111, from left to right, each bit represents A, K, Q, J, 10, 9, ..., 2
	public static final int RANK_MASK = (1 << (PER_SUIT_NUM)) - 1; 
	// Binary: 1111,from left to right, each bit represents spade, club, heart, diamond																					 
	public static final int SUIT_MASK = (1 << (SUIT_TYPE_NUM)) - 1; 
	public static final long FACE_STATUS_MASK = (1L << (ALL_CARD_NUM)) - 1;
	// Special rank : 2, 3, 5
	public static final int TYPE_SPECIAL = 0x1FF4;  // 1 1111 1111 0100
	// Rank : A, 2, 3 , of type STRAIGHT
	public static final int RANK_MASK_A23 = 0xFFC;   // 0 1111 1111 1100
	
	
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
	public static final int USER_INFO_AUTO_BET 		      = 0x1;    // 00 0000 0000 0001
	public static final int USER_INFO_CHECKED_CARD        = 0x2;    // 00 0000 0000 0010
	public static final int USER_INFO_FOLDED_CARD         = 0x4;    // 00 0000 0000 0100
	public static final int USER_INFO_SHOWED_CARD         = 0x8;    // 00 0000 0000 1000
	public static final int USER_INFO_COMPARE_LOSE        = 0x10;   // 00 0000 0001 0000
	public static final int USER_INFO_ACTION_NONE         = 0x20;   // 00 0000 0010 0000
	public static final int USER_INFO_ACTION_BET          = 0x40;   // 00 0000 0100 0000
	public static final int USER_INFO_ACTION_RAISE_BET    = 0x80;   // 00 0000 1000 0000
	public static final int USER_INFO_ACTION_AUTO_BET     = 0x100;  // 00 0001 0000 0000
	public static final int USER_INFO_ACTION_CHECK_CARD   = 0x200;  // 00 0010 0000 0000
	public static final int USER_INFO_ACTION_FOLD_CARD    = 0x400;  // 00 0100 0000 0000
	public static final int USER_INFO_ACTION_COMPARE_CARD = 0x800;  // 00 1000 0000 0000
	public static final int USER_INFO_ACTION_SHOW_CARD    = 0x1000; // 01 0000 0000 0000
	public static final int USER_INFO_ACTION_CHANGE_CARD  = 0x2000; // 10 0000 0000 0000
	public static final int USER_INFO_INITIAL_VALUE 		= 0x20;   // 00 0000 0010 0000, only set ACTION_NONE
	public static final int LAST_ACTION_MASK 			      = 0x3FE0; // 11 1111 1110 0000, used to clear last action

   // For comparing card
	public final static double WINNER_TAX_RATE = 0.1;
	public static final int COMPARE_CHANLLEGER_LOSS_MULTIPLY_FACTOR = 4;

	// Single bet by rule type 
	public static final int SINGLE_BET_BEGINER = 5;
	public static final int SINGLE_BET_NORMAL = 5;
	public static final int SINGLE_BET_DUAL = 10;
	public static final int SINGLE_BET_RICH = 25;

	// Maxinum ante by rule type
	public static final int MAX_ANTE_BEGINER = 50;
	public static final int MAX_ANTE_NORMAL = 50;
	public static final int MAX_ANTE_DUAL = 100;
	public static final int MAX_ANTE_RICH = 250;

	// Total bet threshold by rule type
	public static final int TOTAL_BET_THRESHOLD_BEGINER = 50000;
	public static final int TOTAL_BET_THRESHOLD_NORMAL = 50000;
	public static final int TOTAL_BET_THRESHOLD_DUAL = 500000;
	public static final int TOTAL_BET_THRESHOLD_RICH = 500000;



	

	
	
}
