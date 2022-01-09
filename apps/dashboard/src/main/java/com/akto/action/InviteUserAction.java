package com.akto.action;

import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.notifications.email.SendgridEmail;
import com.akto.utils.JWT;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import com.sendgrid.helpers.mail.Mail;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class InviteUserAction extends UserAction{

    private String inviteeName;
    private String inviteeEmail;
    private String websiteHostName;
    @Override
    public String execute() {
        int user_id = getSUser().getId();

        // only admins are allowed to invite others
        RBAC rbac = RBACDao.instance.findOne(Filters.and(
                Filters.eq(RBAC.USER_ID, user_id), Filters.eq(RBAC.ROLE,RBAC.Role.ADMIN))
        );
        if (rbac == null) {
            return ERROR.toUpperCase();
        }

        Map<String,Object> claims = new HashMap<>();
        claims.put("email", inviteeEmail);

        String inviteCode;

        try {
            inviteCode = JWT.createJWT(
                    "/home/avneesh/Desktop/akto/dashboard/private.pem",
                    claims,
                    "Akto",
                    "invite_user",
                    Calendar.WEEK_OF_MONTH,
                    1
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            // TODO: find better error
            return Action.ERROR.toUpperCase();
        }

        // to get expiry date
        try {
            Jws<Claims> jws = JWT.parseJwt(inviteCode,"");
            PendingInviteCodesDao.instance.insertOne(
                    new PendingInviteCode(inviteCode, user_id, inviteeEmail,jws.getBody().getExpiration().getTime())
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        String finalInviteCode = websiteHostName + "/signup?signupInvitationCode=" + inviteCode + "&signupEmailId=" + inviteeEmail;
        String inviteFrom = getSUser().getName();
        Mail email = SendgridEmail.buildInvitationEmail(inviteeName, inviteeEmail, inviteFrom, finalInviteCode);
        try {
            SendgridEmail.send(email);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    private String invitationCodeToDelete;
    public String deleteInvitationCode() {
        PendingInviteCodesDao.instance.getMCollection().deleteOne(Filters.eq(PendingInviteCode.INVITE_CODE, invitationCodeToDelete));
        return Action.SUCCESS.toUpperCase();
    }

    public void setInvitationCodeToDelete(String invitationCodeToDelete) {
        this.invitationCodeToDelete = invitationCodeToDelete;
    }

    public void setInviteeName(String inviteeName) {
        this.inviteeName = inviteeName;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }

    public void setWebsiteHostName(String websiteHostName) {
        this.websiteHostName = websiteHostName;
    }
}