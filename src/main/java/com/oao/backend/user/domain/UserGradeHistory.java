package com.oao.backend.user.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_grade_history")
public class UserGradeHistory extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private MemberGrade previousGrade;

	@Enumerated(EnumType.STRING)
	private MemberGrade newGrade;

	private Long changedByAdminId;
	private String reason;

	protected UserGradeHistory() {
	}

	public static UserGradeHistory record(
		Long userId,
		MemberGrade previousGrade,
		MemberGrade newGrade,
		Long adminId,
		String reason
	) {
		UserGradeHistory history = new UserGradeHistory();
		history.userId = userId;
		history.previousGrade = previousGrade;
		history.newGrade = newGrade;
		history.changedByAdminId = adminId;
		history.reason = reason;
		return history;
	}
}
