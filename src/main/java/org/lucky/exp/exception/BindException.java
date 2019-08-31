/** Copyright 2019 SAIC General Motors Corporation Ltd. All Rights Reserved.
*
* This software is published under the terms of the SGM Software
* License version 1.0, a copy of which has been included with this
* distribution in the LICENSE.txt file.
*
* @Project Name : LuckyExp
*
* @File name : BindException.java
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
package org.lucky.exp.exception;
/**
 * 绑定异常
*  在对象里绑定参数和公式会发生此异常
* @author FayeWong
* @date 2019年8月30日
 */
public class BindException extends Exception{
	private static final long serialVersionUID = 1L;
	public BindException() {
        super();
    }

    public BindException(String s) {
        super(s);
    }
    public BindException(String message, Throwable cause) {
        super(message, cause);
    }
    public BindException(Throwable cause) {
        super(cause);
    }
}