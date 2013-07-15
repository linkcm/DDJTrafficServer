
package com.orange.game.ddj.robot.client;

import java.util.Random;


public class DdjRobotChatContent {
	
	private final static int IDX_CONTENT = 0;
	private final static int IDX_CONTENTID = 1;
	
	private DdjRobotChatContent() { }
	private static DdjRobotChatContent chatContent = new DdjRobotChatContent();
	
	public static DdjRobotChatContent getInstance() {
		return chatContent;
	}

	enum ChatType { 
		TEXT { 
			String id() { return "1"; }
		},
		EXPRESSION {
			String id() { return "2"; }
		}; 
		
		abstract String id(); 
	}

	
	
   enum Attribute {POSITIVE, NEGATIVE }
   enum Expression {
		// positive
		SMILE(Attribute.POSITIVE)      { String id() { return "[smile]"; }      },
		HAPPY(Attribute.POSITIVE)      { String id() { return "@happy@"; }      },
		PROUND(Attribute.POSITIVE)     { String id() { return "[proud]"; }      },
		LOVELY(Attribute.POSITIVE)     { String id() { return "@lovely@"; }     },
		// negative
		CRY(Attribute.POSITIVE)        { String id() { return "@cry@"; }        },
		EMBARRASS(Attribute.NEGATIVE)  { String id() { return "[embarrass]"; }  },
		ANGER(Attribute.NEGATIVE)      { String id() { return "[anger]"; }      },
		RANDY(Attribute.NEGATIVE)      { String id() { return "@randy@"; }      }, 
		SHOCK(Attribute.NEGATIVE)      { String id() { return "@shock@"; }      },
		SHY(Attribute.NEGATIVE)        { String id() { return "@shy@"; }        },
		SLEEP(Attribute.NEGATIVE)      { String id() { return "@sleep@"; }      },
		CRAZY(Attribute.NEGATIVE)      { String id() { return "@crazy@"; }      },
		WORRY(Attribute.NEGATIVE)      { String id() { return "[wry]"; }        };
		
		private final Attribute attribute;
		private static class IntHolder {
		    static int negativeOffset = 0;
		}
		
		private Expression(Attribute attr) { 
			this.attribute = attr; 
	      if ( attr == Attribute.POSITIVE ) {
            IntHolder.negativeOffset++;
	    	}
		}
		
		static int negativeoffset() {  return IntHolder.negativeOffset;}	
		static int allEnumsNum() { return values().length; }
		Attribute getAttribute() { return attribute;}
		
		abstract String id();
	}
	
	enum VoiceContent {
			URGE("快点吧，我等到花儿都谢了"), 
			HELLO("大家好"),
			CHALLENGE("敢跟老子火拼一把吗"),
			BRAVE("你敢跟，我就敢跟，谁怕谁啊"),
			SHITLUCK("运气真差，老拿烂牌"),
			AFK("我先离开一会"),
			BYEBYE("我要走啦，下次再玩"),
			CONCENTRATE("别吵啦，专心游戏");
			
			private final String content;
			private VoiceContent(String content) {this.content = content;}
			
			public String content() {
				return content;
			}
			public String id() {
				return Integer.toString(ordinal()+1);
			}
	};
	
			
	public String[] getExpression(Expression expression) {
		
		String[] result = {null, null};
		
		result[IDX_CONTENT] = "NULL"; 
		result[IDX_CONTENTID] = expression.id();
		
		return result.clone();
	};
	
	public String[] getExpressionByMeaning(Attribute attribute) {
		
		String[] result = {null, null};
		int index;
		Random rdm = new Random();
		
		int negativeOffset = Expression.negativeoffset();
		int allEnumNum = Expression.allEnumsNum();
		if (attribute == Attribute.POSITIVE) {
			index = rdm.nextInt(negativeOffset); 
		} 
		else {
			index = rdm.nextInt(allEnumNum-negativeOffset)+negativeOffset;
		}
		
		Expression expression = Expression.values()[index];
		result[IDX_CONTENT] = "NULL";
		result[IDX_CONTENTID] = expression.id();
		
		return result.clone();
	}
	
	public  String[] getContent(VoiceContent voice) {
			
			// index 0 : chat content
			// index 1 : content voiceId or expressionId, depent on contentType
			String[] result = {null, null};
		
			result[IDX_CONTENT] = voice.content();
			result[IDX_CONTENTID] = voice.id();
			
			return result;
	};
}