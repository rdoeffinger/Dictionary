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

package com.pras.auth;

public interface Authenticator {

	/**
	 * Do the authentication and read Auth_Token from Server. The implementor class
	 * will provide Account (Gmail) and Service details
	 * 
	 * @param service Name of the Service for which Authentication Token is required e.g. "wise" for SpreadSheet
	 * @return
	 */
	public String getAuthToken(String service);
}
