/** Copyright 2019 SAIC General Motors Corporation Ltd. All Rights Reserved.
*
* This software is published under the terms of the SGM Software
* License version 1.0, a copy of which has been included with this
* distribution in the LICENSE.txt file.
*
* @Project Name : LuckyExp
*
* @File name : Expression.java
*
* @Author : FayeWong
*
* @Email : 50125289@qq.com
*
----------------------------------------------------------------------------------
*    Who        Version     Comments
* 1. FayeWong    1.0
*
*
*
*
----------------------------------------------------------------------------------
*/
package org.lucky.exp;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lucky.exp.annotation.BindDouble;
import org.lucky.exp.annotation.Condition;
import org.lucky.exp.annotation.ExceptionCode;
import org.lucky.exp.exception.CallBackException;
import org.lucky.exp.exception.UnknownFunOrVarException;
import org.lucky.exp.exception.UnknownRuntimeException;
import org.lucky.exp.function.Func;
import org.lucky.exp.function.Funcs;
import org.lucky.exp.missYaner.MissYaner;
import org.lucky.exp.operator.Operator;
import org.lucky.exp.parent.ValiResult;
import org.lucky.exp.tokenizer.FunctionToken;
import org.lucky.exp.tokenizer.NumberToken;
import org.lucky.exp.tokenizer.OperatorToken;
import org.lucky.exp.tokenizer.Token;
import org.lucky.exp.tokenizer.VariableToken;
/**
 * 
*
* @author FayeWong
* @date 2019年8月28日
 */
public class Expression {
	/**解析字符串获取的认证数组（函数，运算符，左括号，右括号，逗号...）**/
    private  Token[] tokens;
    /**条件参数**/
    private  final Map<String, Double> variables;
    /**自定义函数名**/
    private  final Set<String> userFunctionNames;
    /**直接结果集**/
    private  final List<Map<Condition, Object>> passExps;
    /**等待直接结果集**/
    private  final List<Map<Condition, Object>> waitExps; 
    /**用户自定义的函数，不包含内置的函数**/
    private  final Map<String, Func> userFuncs;
    /**用户自定义的运算符，不包含内置运算符**/
    private  final Map<String, Operator> userOperators;
    /**参数名**/
    private  final Set<String> variableNames;   
    /**重算上限**/
    private  int recalLimit;
    /**是否追加隐式乘法**/
    private  final boolean implicitMultiplication;
    private  Serializable entity;
    private static Map<String, Double> createDefaultVariables() {
        final Map<String, Double> vars = new HashMap<String, Double>(4);
        vars.put("pi", Math.PI);
        vars.put("π", Math.PI);
        vars.put("φ", 1.61803398874d);
        vars.put("e", Math.E);
        return vars;
    }   
    Expression(final Map<String, Func> userFuncs,
    		   final Map<String, Operator> userOperators,
    		   final Set<String> variableNames,
    		   final boolean implicitMultiplication,
    		   final List<Map<Condition, Object>> passExps,
    		   final List<Map<Condition, Object>> waitExps,
    		   final int recalLimit,
    		   final Set<String> userFunctionNames){
    	this.userFuncs = userFuncs;
    	this.userOperators = userOperators;
    	this.variableNames = variableNames;
    	this.implicitMultiplication = implicitMultiplication;
    	this.passExps = passExps;
    	this.waitExps = waitExps;
    	this.recalLimit = recalLimit;
    	this.userFunctionNames = userFunctionNames;
    	this.variables = createDefaultVariables();
    }
    public Expression setVariable(final String name, final double value) {
        this.checkVariableName(name);
        this.variables.put(name, Double.valueOf(value));
        return this;
    }

    private void checkVariableName(String name) {
        if (this.userFunctionNames.contains(name) || Funcs.getBuiltinFunction(name) != null) {
            throw new IllegalArgumentException("变量名称无效或函数名重复： '" + name + "'");
        }
    }

    public Expression setVariables(Map<String, Double> variables) {
        for (Map.Entry<String, Double> v : variables.entrySet()) {
        	if(v.getValue() == null) {
        		throw new IllegalArgumentException("变量名称 ' " + v.getKey() + "'没有值!");
        	}
            this.setVariable(v.getKey(), v.getValue());
        }
        return this;
    }
    public Expression setTokens(Token[] tokens) {
    	this.tokens = tokens;
    	return this;
    }
    public Expression setEntity(Serializable entity) {
    	this.entity = entity;
    	return this;
    }
    public Set<String> getVariableNames() {
        final Set<String> variables = new HashSet<String>();
        for (final Token t: tokens) {
            if (t.getType() == Token.TOKEN_VARIABLE)
                variables.add(((VariableToken)t).getName());
        }
        return variables;
    }

    /**
     * 从栈堆推送结果
    *
    * @author FayeWong
    * @date 2019年8月30日
    * @return
     * @throws CallBackException 
     */
    private double evaluate() throws CallBackException {
        final ArrayStack output = new ArrayStack();
        for (int i = 0; i < tokens.length; i++) {
            Token t = tokens[i];
            if (t.getType() == Token.TOKEN_NUMBER) {
                output.push(((NumberToken) t).getValue());
            } else if (t.getType() == Token.TOKEN_VARIABLE) {
                final String name = ((VariableToken) t).getName();
                final Double value = this.variables.get(name);
                if (value == null) {
                    throw new CallBackException("参数为空 '" + name + "'.");
                }
                output.push(value);
            } else if (t.getType() == Token.TOKEN_OPERATOR) {
                OperatorToken op = (OperatorToken) t;
                if (output.size() < op.getOperator().getNumOperands()) {
                    throw new CallBackException("可用于的操作数无效 '" + op.getOperator().getSymbol() + "' operator");
                }
                if (op.getOperator().getNumOperands() == 2) {
                    /* 弹出操作数并推送操作结果 */
                    double rightArg = output.pop();
                    double leftArg = output.pop();
                    output.push(op.getOperator().apply(leftArg, rightArg));
                } else if (op.getOperator().getNumOperands() == 1) {
                	/* 弹出操作数并推送操作结果 */
                    double arg = output.pop();
                    output.push(op.getOperator().apply(arg));
                }
            } else if (t.getType() == Token.TOKEN_FUNCTION) {
                FunctionToken func = (FunctionToken) t;
                final int numArguments = func.getFunction().getNumArguments();
                if (output.size() < numArguments) {
                    throw new CallBackException("可用于的参数数目无效 '" + func.getFunction().getName() + "' function");
                }
                /* 从堆栈收集参数 */
                double[] args = new double[numArguments];
                for (int j = numArguments - 1; j >= 0; j--) {
                    args[j] = output.pop();
                }
                output.push(func.getFunction().apply(args));
            }
        }
        if (output.size() > 1) {
            throw new CallBackException("输出队列中的项目数无效。可能是函数的参数数目无效导致的.");
        }
        return output.pop();
    }
    
    /**
     * 从表达式中推送出来的结果组装到各个对象中
    *
    * @author FayeWong
    * @date 2019年8月30日
    * @param exps
    * @return
     */
    private boolean convertToBean(List<Map<Condition, Object>> exps,ValiResult valiResult) {		
		LinkedList<Map<Condition, Object>> pop = (LinkedList<Map<Condition, Object>>) exps;
		boolean isSuccess = false;
		for (int i = 0; i < pop.size(); i++) {
			Map<Condition, Object> exp = pop.remove(i);
			i--;
			    Field field = (Field) exp.get(Condition.field);
			try {
				String expression = (String) exp.get(Condition.calculation);
				Token[] tokens = MissYaner.convertToRPN(expression, this.userFuncs, this.userOperators,this.variableNames, this.implicitMultiplication);
				this.tokens = tokens;
				if(valiResult != null) {
					valiResult.validate(tokens,this.variables,field);
				}
				double result = evaluate();
				result = Double.valueOf(new DecimalFormat(exp.get(Condition.format).toString()).format(result));
				field.set(exp.get(Condition.entity), result);
				BindDouble bind = field.getAnnotation(BindDouble.class);
				if (bind != null) {
					PropertyDescriptor pd = new PropertyDescriptor(field.getName(),  
							exp.get(Condition.entity).getClass());  
					Method getMethod = pd.getReadMethod();
		            Object doubleVal = getMethod.invoke((Object)exp.get(Condition.entity));
		            this.variables.put(bind.key(), (Double) doubleVal);
		            this.variableNames.addAll(variables.keySet());
				}
			} catch (UnknownFunOrVarException e) {
				this.recalLimit --;
				if (this.recalLimit == 0) {
					//发生回滚异常需要及时把结果给回调函数
					if(valiResult != null) {
						valiResult.setT(entity,true);
					}
					throw new UnknownRuntimeException(ExceptionCode.C_10044.getCode(), e);
				}
				pop.offerLast(exp);
				convertToBean(pop,valiResult);
			} catch (IllegalArgumentException e) {
				throw new UnknownRuntimeException(ExceptionCode.C_10042.getCode(),e);
			} catch (IllegalAccessException e) {
				throw new UnknownRuntimeException(ExceptionCode.C_10042.getCode(),e);
			} catch (InvocationTargetException e) {
				throw new UnknownRuntimeException(ExceptionCode.C_10042.getCode(),e);
			} catch (IntrospectionException e) {
				throw new UnknownRuntimeException("成员变量 '"+field.getName()+"' 没有get()方法",e);
			} catch (CallBackException e) {
				//发生回滚异常需要及时把结果给回调函数
				if(valiResult != null) {
					valiResult.setT(entity,true);
				}	
				throw new IllegalArgumentException(e);
			}
		}
		if(pop.isEmpty())isSuccess = true;
		return isSuccess;
	}
    public boolean result() {
    	if(!passExps.isEmpty()) {
    		if(convertToBean(passExps,null) && !waitExps.isEmpty()) {
    			return convertToBean(waitExps,null);
    		}else {
    			return true;
    		}
    	} 
    	return false;
    }
    public void result(ValiResult valiResult) {
    	if(!passExps.isEmpty()) {
    		if(convertToBean(passExps,valiResult) && !waitExps.isEmpty()) {
    			if(convertToBean(waitExps,valiResult)) {
    				valiResult.setT(entity,false);
    			};
    		}else {
    			valiResult.setT(entity,false);
    		}
    	} 
    }
}