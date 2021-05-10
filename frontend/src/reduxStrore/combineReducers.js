import { combineReducers } from "redux";

import authReducer from "../containers/Firebase/reducer";
import bankReducer from "../containers/User/bankInfoReducer";
import userInfoReducer from "../containers/User/userInfoReducer";
import userBalanceReducer from "../containers/User/userBalanceReducer";
import orderReducer from "../containers/User/orderReducer";

const data = {
  auth: authReducer,
  bank: bankReducer,
  order: orderReducer,
  userInfo: userInfoReducer,
  userBalance: userBalanceReducer
};

const reducers = combineReducers(data);

export default reducers;
