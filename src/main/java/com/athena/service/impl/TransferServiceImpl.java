package com.athena.service.impl;

import com.athena.annotation.MyAutowired;
import com.athena.annotation.MyService;
import com.athena.annotation.MyTransactional;
import com.athena.dao.AccountDao;
import com.athena.pojo.Account;
import com.athena.service.TransferService;


@MyService("transferService")
public class TransferServiceImpl implements TransferService {


    @MyAutowired("accountDao")
    private AccountDao accountDao;


    @MyTransactional
    @Override
    public void transfer(String fromCardNo, String toCardNo, int money) throws Exception {

        Account from = accountDao.queryAccountByCardNo(fromCardNo);
        Account to = accountDao.queryAccountByCardNo(toCardNo);

        from.setMoney(from.getMoney() - money);
        to.setMoney(to.getMoney() + money);

        accountDao.updateAccountByCardNo(to);
        //int c = 1 / 0;
        accountDao.updateAccountByCardNo(from);
    }
}
