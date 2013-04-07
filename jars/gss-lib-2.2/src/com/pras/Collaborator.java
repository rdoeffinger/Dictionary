/*
 * Copyright (C) 2010 Prasanta Paul, http://prasanta-paul.blogspot.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pras;

/**
 * ACL details of a SpreadSheet
 * 
 * @author Prasanta Paul
 *
 */
public class Collaborator {

	// Defined Roles
	/**
	 * Owner of a SpreadSheet
	 */
	public static String ROLE_OWNER = "owner";
	/**
	 * Able to Read/Write and Further Share as Reader/Writer
	 */
	public static String ROLE_WRITER = "writer";
	/**
	 * Able to Read
	 */
	public static String ROLE_READER = "reader";
	
	// Defined Scopes
	/**
	 * Use this scope to share it with an Email account 
	 */
	public static String SCOPE_USER = "user";
	/**
	 * Use this scope to share it with a Group 
	 */
	public static String SCOPE_GROUP = "group";
	/**
	 * Use this scope to share it with a Domain
	 */
	public static String SCOPE_DOMAIN = "domain";
	/**
	 * Use this scope to make the SpreadSheet publicly accessible to anyuser
	 */
	public static String SCOPE_DEFAULT = "default";
	
	String role;
	String scopeType;
	String scopeValue;
	String editLink;
	
	/**
	 * Get Role - {"owner", "writer", "reader"}
	 * @return
	 */
	public String getRole() {
		return role;
	}
	/**
	 * Set Role - {"owner", "writer", "reader"}
	 * @param role {ROLE_OWNER, ROLE_WRITER, ROLE_READER} 
	 */
	public void setRole(String role) {
		this.role = role;
	}
	/**
	 * Get Scope Type - {"user", "group", "domain", "default"}
	 * @return
	 */
	public String getScopeType() {
		return scopeType;
	}
	/**
	 * Set Scope Type {"user", "group", "domain", "default"}
	 * @param scopeType {SCOPE_USER, SCOPE_GROUP, SCOPE_DOMAIN, SCOPE_DEFAULT}
	 */
	public void setScopeType(String scopeType) {
		this.scopeType = scopeType;
	}
	/**
	 * Get Scope Value e.g. <email address>
	 * @return
	 */
	public String getScopeValue() {
		return scopeValue;
	}
	/**
	 * Set Scope Value
	 * @param scopeValue Email Address
	 */
	public void setScopeValue(String scopeValue) {
		this.scopeValue = scopeValue;
	}
	/**
	 * Get Edit Link
	 * @return
	 */
	public String getEditLink() {
		return editLink;
	}
	/**
	 * Set Edit Link
	 * @param editLink
	 */
	public void setEditLink(String editLink) {
		this.editLink = editLink;
	}
}
