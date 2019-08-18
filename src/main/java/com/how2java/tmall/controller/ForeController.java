package com.how2java.tmall.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.math.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import com.github.pagehelper.PageHelper;
import com.how2java.tmall.comparator.ProductDateComparator;
import com.how2java.tmall.comparator.ProductPriceComparator;
import com.how2java.tmall.comparator.ProductReviewComparator;
import com.how2java.tmall.comparator.ProductSaleCountComparator;
import com.how2java.tmall.pojo.Category;
import com.how2java.tmall.pojo.Order;
import com.how2java.tmall.pojo.OrderItem;
import com.how2java.tmall.pojo.Product;
import com.how2java.tmall.pojo.ProductImage;
import com.how2java.tmall.pojo.PropertyValue;
import com.how2java.tmall.pojo.Review;
import com.how2java.tmall.pojo.User;
import com.how2java.tmall.service.CategoryService;
import com.how2java.tmall.service.OrderItemService;
import com.how2java.tmall.service.OrderService;
import com.how2java.tmall.service.ProductImageService;
import com.how2java.tmall.service.ProductService;
import com.how2java.tmall.service.PropertyValueService;
import com.how2java.tmall.service.ReviewService;
import com.how2java.tmall.service.UserService;

@Controller
@RequestMapping("")
public class ForeController {

	@Autowired
	CategoryService categoryService;
	@Autowired
	ProductService productService;
	@Autowired
	UserService userService;
	@Autowired
	ProductImageService productImageService;
	@Autowired
	PropertyValueService propertyValueService;
	@Autowired
	OrderService orderService;
	@Autowired
	OrderItemService orderItemService;
	@Autowired
	ReviewService reviewService;
	
	@RequestMapping("forehome")
	public String home(Model model){
		List<Category> cs = categoryService.list();
		productService.fill(cs);
		productService.fillByRow(cs);
		model.addAttribute("cs", cs);
		return "fore/home";
	}
	
	@RequestMapping("foreregister")
	public String register(Model model, User user){
		String name = user.getName();
		name = HtmlUtils.htmlEscape(name);
		user.setName(name);
		boolean exist = userService.isExist(name);
		if (exist){
			String msg = "已存在相同用户名，不能使用";
			model.addAttribute("msg", msg);
			model.addAttribute("user", null);
			return "fore/register";
		}
		userService.add(user);
		return "redirect:registerSuccessPage";
	}
	
	@RequestMapping("forelogin")
	public String login(@RequestParam("name") String name, @RequestParam("password") String password, Model model, HttpSession session){
		name = HtmlUtils.htmlEscape(name);
		User user = userService.get(name, password);
		if (user == null){
			model.addAttribute("msg", "账号密码错误");
			return "fore/login";
		}
		session.setAttribute("user", user);
		return "redirect:forehome";
	}
	
	@RequestMapping("forelogout")
	public String logout(HttpSession session){
		session.removeAttribute("user");
		return "redirect:forehome";
	}
	
	@RequestMapping("foreproduct")
	public String product(int pid, Model model){
		Product p = productService.get(pid);
		List<ProductImage> productSingleImages = productImageService.list(pid, ProductImageService.type_single);
		List<ProductImage> productDetailImages = productImageService.list(pid, ProductImageService.type_detail);
		p.setProductSingleImages(productSingleImages);
		p.setProductDetailImages(productDetailImages);
		List<PropertyValue> pvs = propertyValueService.list(pid);
		List<Review> reviews = reviewService.list(pid);
		productService.setSaleAndReviewNumber(p);
		model.addAttribute("reviews", reviews);
		model.addAttribute("pvs", pvs);
		model.addAttribute("p", p);
		return "fore/product";
	}
	
	@RequestMapping("forecheckLogin")
	@ResponseBody
	public String checkLogin(HttpSession session){
		User user = (User) session.getAttribute("user");
		if (user != null)
			return "success";
		return "fail";
	}
	
	@RequestMapping("foreloginAjax")
	@ResponseBody
	public String loginAjax(@RequestParam("name") String name, @RequestParam("password") String password, HttpSession session){
		name = HtmlUtils.htmlEscape(name);
		User user = userService.get(name, password);
		if (user == null)
			return "fail";
		session.setAttribute("user", user);
		return "success";
	}
	
	@RequestMapping("forecategory")
	public String category(int cid, String sort, Model model){
		Category c = categoryService.get(cid);
		productService.fill(c);
		productService.setSaleAndReviewNumber(c.getProducts());
		
		if (sort != null){
			switch (sort) {
			case "review":
				Collections.sort(c.getProducts(), new ProductReviewComparator());
				break;
			case "saleCount":
				Collections.sort(c.getProducts(), new ProductSaleCountComparator());
				break;
			case "price":
				Collections.sort(c.getProducts(), new ProductPriceComparator());
				break;
			case "date":
				Collections.sort(c.getProducts(), new ProductDateComparator());
				break;
			case "all":
				Collections.sort(c.getProducts(), new ProductReviewComparator());
				break;
			}
		}
		model.addAttribute("c", c);
		return "fore/category";
	}
	
	@RequestMapping("foresearch")
	public String search(String keyword, Model model){
		PageHelper.offsetPage(0, 20);
		List<Product> ps = productService.search(keyword);
		model.addAttribute("ps", ps);
		return "fore/searchResult";
	}
	
	@RequestMapping("forebuyone")
	public String buyone(int pid, int num, HttpSession session){
		Product p = productService.get(pid);
		int oiid = 0;
		
		User user = (User) session.getAttribute("user");
		boolean found = false;
		List<OrderItem> ois = orderItemService.listByUser(user.getId());
		//如果订单项商品存在在购物车里（即已有orderitem记录），那就直接增加订单项商品的数量:
		for (OrderItem oi:ois){
			if (oi.getProduct().getId().intValue() == p.getId().intValue()){
				oi.setNumber(oi.getNumber() + num);
				orderItemService.update(oi);
				found = true;
				oiid = oi.getId();
				break;
			}
		}
		//如果订单项商品不存在在购物车里（即无orderitem记录），那就新建一个orderitem
		if (!found){
			OrderItem oi = new OrderItem();
			oi.setUid(user.getId());
			oi.setNumber(num);
			oi.setPid(pid);
			orderItemService.add(oi);
			oiid = oi.getId();
		}
		return "redirect:forebuy?oiid=" + oiid;
	}
	
	@RequestMapping("forebuy")
	public String buy(Model model, String[] oiid, HttpSession session){
		List<OrderItem> ois = new ArrayList<>();
		float total = 0;
		for (String strid:oiid){
			int id = Integer.parseInt(strid);
			OrderItem oi = orderItemService.get(id);
			total += oi.getNumber() * oi.getProduct().getPromotePrice();
			ois.add(oi);
		}
		session.setAttribute("ois", ois);
		model.addAttribute("total", total);
		return "fore/buy";
	}
	
	@RequestMapping("foreaddCart")
	@ResponseBody
	public String addCart(int pid, int num, Model model, HttpSession session){
		Product p = productService.get(pid);
		User user = (User) session.getAttribute("user");
		boolean found = false;
		
		List<OrderItem> ois = orderItemService.listByUser(user.getId());
		for (OrderItem oi:ois){
			if (oi.getProduct().getId().intValue() == p.getId().intValue()){
				oi.setNumber(oi.getNumber() + num);
				orderItemService.update(oi);
				found = true;
				break;
			}
		}
		if (!found){
			OrderItem oi = new OrderItem();
			oi.setUid(user.getId());
			oi.setNumber(num);
			oi.setPid(p.getId());
			orderItemService.add(oi);
		}
		return "success";
	}
	
	@RequestMapping("forecart")
	public String cart(Model model, HttpSession session){
		User user = (User) session.getAttribute("user");
		List<OrderItem> ois = orderItemService.listByUser(user.getId());
		model.addAttribute("ois", ois);
		return "fore/cart";
	}
	
	@RequestMapping("forechangeOrderItem")
	@ResponseBody
	public String changeOrderItem(Model model, HttpSession session, int pid, int number){
		User user = (User) session.getAttribute("user");
		if (user == null){
			return "fail";
		}
		List<OrderItem> ois = orderItemService.listByUser(user.getId());
		for (OrderItem oi:ois){
			if (oi.getProduct().getId().intValue() == pid){
				oi.setNumber(number);
				orderItemService.update(oi);
				break;
			}
		}
		return "success";
	}
	
	@RequestMapping("fordeleteOrderItem")
	@ResponseBody
	public String deleteOrderItem(Model model, HttpSession session, int oiid){
		User user = (User) session.getAttribute("user");
		if (user == null)
			return "fail";
		orderItemService.delete(oiid);
		return "success";
	}
	
	@RequestMapping("forecreateOrder")
	public String createOrder(Model model, Order order, HttpSession session){
		User user = (User) session.getAttribute("user");
		String orderCode = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + RandomUtils.nextInt(10000);
		order.setOrderCode(orderCode);
		order.setCreateDate(new Date());
		order.setUid(user.getId());
		order.setStatus(OrderService.waitPay);
		List<OrderItem> ois = (List<OrderItem>) session.getAttribute("ois");
		
		float total = orderService.add(order, ois);
		return "redirect:forealipay?oid=" + order.getId() + "&total=" + total;
	}
	
	@RequestMapping("forepayed")
	public String payed(int oid, float total, Model model){
		Order order = orderService.get(oid);
		order.setStatus(OrderService.waitDelivery);
		order.setPayDate(new Date());
		orderService.update(order);
		model.addAttribute("o", order);
		return "fore/payed";
	}
	
	@RequestMapping("forebought")
	public String bought(Model model, HttpSession session){
		User user = (User) session.getAttribute("user");
		List<Order> os = orderService.list(user.getId(), OrderService.delete);
		orderItemService.fill(os);
		model.addAttribute("os", os);
		return "fore/bought";
	}
	
	@RequestMapping("foreconfirmPay")
	public String confirmPay(Model model, int oid){
		Order o = orderService.get(oid);
		orderItemService.fill(o);
		model.addAttribute("o", o);
		return "fore/confirmPay";
	}
	
	@RequestMapping("foreorderConfirmed")
	public String orderConfirmed(Model model, int oid){
		Order o = orderService.get(oid);
		o.setStatus(OrderService.waitReview);
		o.setConfirmDate(new Date());
		orderService.update(o);
		return "fore/orderConfirmed";
	}
	
	@RequestMapping("foredeleteOrder")
	@ResponseBody
	public String deleteOrder(Model model, int oid){
		Order o = orderService.get(oid);
		o.setStatus(OrderService.delete);
		orderService.update(o);
		return "success";
	}
	
	@RequestMapping("forereview")
	public String review(Model model, int oid){
		Order o = orderService.get(oid);
		orderItemService.fill(o);
		Product p = o.getOrderItems().get(0).getProduct();
		List<Review> reviews = reviewService.list(p.getId());
		productService.setSaleAndReviewNumber(p);
		model.addAttribute("p", p);
		model.addAttribute("o", o);
		model.addAttribute("reviews", reviews);
		return "fore/review";
	}
	
	@RequestMapping("foredoreview")
	public String doreview(Model model, HttpSession session, @RequestParam("oid") int oid, 
			@RequestParam("pid") int pid, String content){
		Order o = orderService.get(oid);
		o.setStatus(OrderService.finish);
		orderService.update(o);
		
		Product p = productService.get(pid);
		content = HtmlUtils.htmlEscape(content);
		
		User user = (User) session.getAttribute("user");
		Review review = new Review();
		review.setContent(content);
		review.setUid(user.getId());
		review.setPid(pid);
		review.setCreateDate(new Date());
		reviewService.add(review);
		return "redirect:forereview?oid=" + oid + "&showonly=true";
	}
	
}
