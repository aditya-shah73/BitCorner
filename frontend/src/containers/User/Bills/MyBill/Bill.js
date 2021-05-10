import React,{ useEffect } from 'react';
import Link from '@material-ui/core/Link';
import { makeStyles } from '@material-ui/core/styles';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import Typography from '@material-ui/core/Typography';
import { requestBillInfo,requestCurrencyInfo } from "../action";
import PropTypes from "prop-types";
import { connect } from "react-redux";
import EditBillModal from "./EditBill"
// import Title from './Title';
import EditIcon from '@material-ui/icons/Edit';
// Generate Order Data
function createData(id, userName, description, amount, paymentMethod,date, status) {
  return { id, userName, description, amount, paymentMethod, date,status };
}

let rows = [
  createData(0, 'Dshah9542@gmail.com', 'PIzza', '50', 'Dollar', 'Pending'),
  // createData(1, '16 Mar, 2019', 'Paul McCartney', 'London, UK', 'VISA ⠀•••• 2574', 866.99),
  // createData(2, '16 Mar, 2019', 'Tom Scholz', 'Boston, MA', 'MC ⠀•••• 1253', 100.81),
  // createData(3, '16 Mar, 2019', 'Michael Jackson', 'Gary, IN', 'AMEX ⠀•••• 2000', 654.39),
  // createData(4, '15 Mar, 2019', 'Bruce Springsteen', 'Long Branch, NJ', 'VISA ⠀•••• 5919', 212.79),
];

function preventDefault(event) {
  event.preventDefault();
}

const useStyles = makeStyles((theme) => ({
  seeMore: {
    marginTop: theme.spacing(3),
  },
}));
function Bill(props) {
  const classes = useStyles();
  const {  onrequestCurrencyInfo, currency, bills, onrequestBillInfo} = props;
  rows=[]
  bills.bills.map((bill)=>rows.push(createData(bill, bill.toUser.userName, bill.description, bill.amount, bill.targetCurrency.name, bill.dueDate,bill.status)))
  console.log(currency);
  console.log(bills);
  useEffect(() => {
    
    onrequestBillInfo();
    onrequestCurrencyInfo();

    

  }, []);
  return (
    <React.Fragment>
          <Typography component="h2" variant="h6" color="primary" gutterBottom>
      Your Bills
      
    </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>To</TableCell>
            <TableCell>Description</TableCell>
            <TableCell>amount</TableCell>
            <TableCell>Currency</TableCell>
            <TableCell align="right">Due Date</TableCell>
            <TableCell align="right">Status</TableCell>
            <TableCell align="right">Edit</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{row.userName}</TableCell>
              <TableCell>{row.description}</TableCell>
              <TableCell>{row.amount}</TableCell>
              <TableCell>{row.paymentMethod}</TableCell>
              <TableCell align="right">{row.date}</TableCell>
              <TableCell align="right">{row.status}</TableCell>
              <TableCell align="right"><EditBillModal bill = {row.id}></EditBillModal></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {/* <div className={classes.seeMore}>
        <Link color="primary" href="#" onClick={preventDefault}>
          See more orders
        </Link>
      </div> */}
    </React.Fragment>
  );
}
const mapStateToProps = (state) => {
  console.log(state);
  return {
    bills: state.bill,
    currency :state.currency
  };
};

const mapDispatchToProps = (dispatch) => {
  return {
    
    onrequestCurrencyInfo: ()  => dispatch(requestCurrencyInfo()),
    onrequestBillInfo: () => dispatch(requestBillInfo())
  };
};



Bill.propTypes = {
  classes: PropTypes.object.isRequired,
};

// export default (props) => (
//   <ComponentWrapper
//     name="Bank Account Details"
//     helperText="View and update your bank account information"
//     Component={connect(
//       mapStateToProps,
//       mapDispatchToProps
//     )(withStyles(styles)(Content))}
//     {...props}
//   />
// );
export default connect(
  mapStateToProps,
  mapDispatchToProps
)(Bill);