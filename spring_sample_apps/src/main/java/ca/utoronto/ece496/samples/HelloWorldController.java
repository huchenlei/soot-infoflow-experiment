package ca.utoronto.ece496.samples;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Charlie on 10. 01 2019
 */

@RestController
@Controller
public class HelloWorldController {
    @RequestMapping("/hello")
    public String hello() {
        return "Hello World!";
    }


    /**
     * This is a sample method to be tested
     *
     * UserName should be treated as a taint source
     *
     * @param userName user name passed from user's request
     * @return a simple hello string
     */
    @RequestMapping("/user")
    public String userPage(@RequestParam String userName) {
        return "this is" + userName;
    }
}
