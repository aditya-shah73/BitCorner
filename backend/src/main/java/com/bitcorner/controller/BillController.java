package com.bitcorner.controller;

import com.bitcorner.auth.SecurityService;
import com.bitcorner.dataModel.ErrorResponse;
import com.bitcorner.dataModel.SuccessResponse;
import com.bitcorner.entity.*;
import com.bitcorner.repository.MarketPriceRepository;
import com.bitcorner.service.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bitcorner.service.MessageService;
import com.bitcorner.service.CurrencyService;
import com.bitcorner.service.UserInfoService;
import com.bitcorner.service.BalanceService;

import javax.management.BadAttributeValueExpException;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/bill")
public class BillController
{
    @Autowired
    BalanceService balanceService;

    @Autowired
    UserInfoService userInfoService;

    @Autowired
    MessageService messageService;

    @Autowired
    CurrencyService currencyService;
    @Autowired
    SecurityService securityService;
    @Autowired
    BillService billService;
    @Autowired
    private MarketPriceRepository marketPriceRepository;

    @Autowired
    private HttpServletRequest request;

    @RequestMapping(method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> update(
            @RequestParam(name = "ID", required = true) Long id,
            @RequestParam(name = "Description", required = true) String Description,
            @RequestParam(name = "target_currency", required = true) Long currid,
            @RequestParam(name = "amount", required = true) BigDecimal amount,
            @RequestParam(name = "duedate", required = true) @DateTimeFormat(pattern="yyyy-MM-dd") Date DueDate
    )
    {
        try
        {
            Currency currency = currencyService.getById(currid);
            System.out.println(id);
            Bill bill = billService.getById(id);

            bill.setDescription(Description);
            bill.setAmount(amount);
            bill.setDueDate(DueDate);
            Date today = new Date();
            if(DueDate.compareTo(today) < 0)
            {
                bill.setStatus("Overdue");
            }
            else {
                bill.setStatus("Waiting");
            }
            bill.setTargetCurrency(currency);

            billService.update(bill);
            messageService.sendBill(bill, "Notification from Bitcorner: Updated Bill");

            return new ResponseEntity<>(bill, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        } catch (BadAttributeValueExpException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.toString()), HttpStatus.BAD_REQUEST);
        } catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> create(@RequestParam(name = "toEmail", required = true) String toEmail,
                                    @RequestParam(name = "Description", required = true) String Description,
                                    @RequestParam(name = "target_currency", required = true) Long currid,
                                    @RequestParam(name = "amount", required = true) BigDecimal amount,
                                    @RequestParam(name = "duedate", required = true) @DateTimeFormat(pattern="yyyy-MM-dd")Date DueDate
    )
    {
        try
        {
            String fromID = getUserId();
            if (fromID == null || fromID.isEmpty())
            {
                throw new BadAttributeValueExpException("Invalid UserId");
            }
            UserInfo fromuser = userInfoService.getById(fromID);
            UserInfo userto = userInfoService.getByUserName(toEmail);
            if (fromuser.getUserName().equals(userto.getUserName()))
            {
                throw new BadAttributeValueExpException("Cannot send bill to yourself");
            }
            Currency currency = currencyService.getById(currid);
            Date today = new Date();
            Bill bill = new Bill(fromID, userto.getId(), currency, amount, DueDate, Description);
            bill.setToUser(userto);
            bill.setFromUser(fromuser);
            if(DueDate.compareTo(today) < 0)
            {
                bill.setStatus("Overdue");
            }
            else {
                bill.setStatus("Waiting");
            }
            Date d = new Date();
            bill.setTime(d);
            bill.setServiceFee(BigDecimal.valueOf(0));
            billService.create(bill);
            messageService.sendBill(bill, "Notification from Bitcorner: New Bill");
            return new ResponseEntity<>(bill, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        } catch (BadAttributeValueExpException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.toString()), HttpStatus.BAD_REQUEST);
        } catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/pay", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> paybill(@RequestParam(name = "ID", required = true) Long id,

                                     @RequestParam(name = "pay_currency", required = true) Long currid2
    )
    {
        try
        {


            Bill bill = billService.getById(id);
            if (currid2 == bill.getTargetCurrency().getId())
            {
                balanceService.withdrawBalance(bill.getToUserId(), currid2, bill.getAmount());
                balanceService.depositBalance(bill.getFromUserId(), currid2, bill.getAmount());
                bill.setStatus("Paid");
                bill.setServiceFee(new BigDecimal(0));
            } else if (currid2 == 6 || bill.getTargetCurrency().getId() == 6)
            {
                if (currid2 == 6)
                {
                    MarketPrice mp = marketPriceRepository.getOne(bill.getTargetCurrency().getId());
                    BigDecimal payamount = bill.getAmount().divide(mp.getTransactionPrice(), RoundingMode.HALF_UP);
                    BigDecimal payamountWithMargin = payamount.multiply(BigDecimal.valueOf(1.05));
                    BigDecimal payamountWithServiceFee = payamountWithMargin.multiply(BigDecimal.valueOf(1.0001));
                    balanceService.withdrawBalance(bill.getToUserId(), currid2, payamountWithServiceFee);
                    balanceService.depositBalance(bill.getFromUserId(), bill.getTargetCurrency().getId(), bill.getAmount());
                    bill.setStatus("Paid");
                    bill.setServiceFee(payamountWithServiceFee.subtract(payamount));
                } else
                {
                    MarketPrice mp = marketPriceRepository.getOne(currid2);
                    BigDecimal payamount = bill.getAmount().multiply(mp.getTransactionPrice());
                    BigDecimal payamountWithMargin = payamount.multiply(BigDecimal.valueOf(1.05));
                    BigDecimal payamountWithServiceFee = payamountWithMargin.multiply(BigDecimal.valueOf(1.0001));
                    balanceService.withdrawBalance(bill.getToUserId(), currid2, payamountWithServiceFee);
                    balanceService.depositBalance(bill.getFromUserId(), bill.getTargetCurrency().getId(), bill.getAmount());
                    bill.setStatus("Paid");
                    bill.setServiceFee(payamountWithServiceFee.subtract(payamount));
                }

            } else
            {
                BigDecimal payamount = currencyService.convertAmount(bill.getTargetCurrency().getId(), currid2, bill.getAmount());
                balanceService.withdrawBalance(bill.getToUserId(), currid2, payamount.multiply(BigDecimal.valueOf(1.0001)));
                balanceService.depositBalance(bill.getFromUserId(), bill.getTargetCurrency().getId(), bill.getAmount());
                bill.setStatus("Paid");
                bill.setServiceFee(payamount.multiply(BigDecimal.valueOf(0.0001)));

            }
            Currency paidCurrency = currencyService.getById(currid2);
            bill.setPaidCurrency(paidCurrency);
            billService.update(bill);
            messageService.sendBill(bill, "Notification from Bitcorner: Payed Bill");
            return new ResponseEntity<>(bill, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        } catch (BadAttributeValueExpException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.toString()), HttpStatus.BAD_REQUEST);
        } catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/cancel", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> cancelBill(@RequestParam(name = "ID", required = true) Long id
    )
    {
        try
        {
            Bill bill = billService.getById(id);
            bill.setStatus("Cancelled");

            billService.update(bill);
            messageService.sendBill(bill, "Notification from Bitcorner: Cancelled Bill");
            return new ResponseEntity<>(bill, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        } catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/reject", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> rejectBill(@RequestParam(name = "ID", required = true) Long id
    )
    {
        try
        {


            Bill bill = billService.getById(id);
            bill.setStatus("Rejected");

            billService.update(bill);
            messageService.sendBill(bill, "Notification from Bitcorner: Rejected Bill");
            return new ResponseEntity<>(bill, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        } catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/own", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getall()
    {
        try
        {
            String fromID = getUserId();
            List<Bill> bills = billService.getByFromEmail(fromID);
            return new ResponseEntity<>(bills, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        }
//        catch (BadAttributeValueExpException ex){
//            return new ResponseEntity<>(new ErrorResponse(ex.toString()), HttpStatus.BAD_REQUEST);
//        }
        catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(value = "/pay", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getallpay(
    )
    {
        try
        {
            String toID = getUserId();
            List<Bill> bills = billService.getBytoEmail(toID);
            return new ResponseEntity<>(bills, HttpStatus.OK);
        } catch (EntityNotFoundException ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        }
//        catch (BadAttributeValueExpException ex){
//            return new ResponseEntity<>(new ErrorResponse(ex.toString()), HttpStatus.BAD_REQUEST);
//        }
        catch (Exception ex)
        {
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(method = RequestMethod.GET,produces = MediaType.APPLICATION_JSON_VALUE, value = "/allBills")
    @ResponseBody
    public ResponseEntity<?> getAllBills(){
        try {
            List<Bill> billList=billService.getAllBills();
            return new ResponseEntity<>(billList, HttpStatus.OK);
        }catch (EntityNotFoundException ex){
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.NOT_FOUND);
        }catch (Exception ex){
            return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String getUserId() throws FirebaseAuthException, NullPointerException
    {
        String token = securityService.getBearerToken(request);
        FirebaseToken decodedToken = null;
        try
        {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
        } catch (FirebaseAuthException e)
        {
            e.printStackTrace();
        }
        System.out.println(decodedToken.getUid());
        return decodedToken.getUid();
    }
}
