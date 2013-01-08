package com.thomsonreuters.lsps.transmart

class UserSettings {
	Long userId
	String name
	String value
	
	// static belongsTo = [AuthUser]
	
	static mapping = {
		version false
		table 'searchapp.search_user_settings'
		id column: 'ID'
		userId column: 'USER_ID'
		name column: 'SETTING_NAME'
		value column: 'SETTING_VALUE'
	}
	
	static String getSetting(Long userid, String name) {
		def res = UserSettings.findByUserIdAndName(userid, name)
		return res?.value
	}
	
	static String setSetting(Long userid, String name, String value) {
		def res = UserSettings.findByUserIdAndName(userid, name)
		if (res) 
			res.value = value
		else 
			res = new UserSettings(userId: userid, name: name, value: value)
		
		res.save()
	}
}

