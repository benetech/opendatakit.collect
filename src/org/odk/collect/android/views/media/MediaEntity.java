package org.odk.collect.android.views.media;

import android.media.MediaPlayer;

public class MediaEntity {
	
	private String source;
	private Object idOfOriginView;
	private MediaPlayer player;
	private ButtonState buttonState;
	
	public MediaEntity(String source, MediaPlayer player, Object id, ButtonState state) {
		this.player = player;
		this.source = source;
		this.idOfOriginView = id;
		this.buttonState = state;
	}
	
	public MediaEntity() {
		
	}
	
	public Object getId() {
		return idOfOriginView;
	}
	
	public ButtonState getState() {
		return buttonState;
	}
	
	public void setPlayer(MediaPlayer mp) {
		this.player = mp;
	}
	
	public void setState(ButtonState state) {
		this.buttonState = state;
	}
	
	public MediaPlayer getPlayer() {
		return player;
	}
	
	public String getSource() {
		return source;
	}
	
}
