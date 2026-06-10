package com.oao.backend.user.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "user_hobby")
public class UserHobby {

	@EmbeddedId
	private UserHobbyId id;

	protected UserHobby() {
	}

	public static UserHobby select(Long userId, Long hobbyId) {
		UserHobby userHobby = new UserHobby();
		userHobby.id = new UserHobbyId(userId, hobbyId);
		return userHobby;
	}

	public UserHobbyId getId() {
		return id;
	}

	@Embeddable
	public static class UserHobbyId implements Serializable {

		private Long userId;
		private Long hobbyId;

		protected UserHobbyId() {
		}

		public UserHobbyId(Long userId, Long hobbyId) {
			this.userId = userId;
			this.hobbyId = hobbyId;
		}

		public Long getUserId() {
			return userId;
		}

		public Long getHobbyId() {
			return hobbyId;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof UserHobbyId that)) {
				return false;
			}
			return Objects.equals(userId, that.userId) && Objects.equals(hobbyId, that.hobbyId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(userId, hobbyId);
		}
	}
}
