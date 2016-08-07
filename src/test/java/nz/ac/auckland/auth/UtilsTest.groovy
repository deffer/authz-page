package nz.ac.auckland.auth

import nz.ac.auckland.auth.contract.KongContract
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.test.context.web.WebAppConfiguration

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = UtilsTest.class)
@WebAppConfiguration
class UtilsTest {

	@Test
	public void trustedHostsCheckPositive() {
		def trustedHosts = ["gelato.io", "auckland.ac.nz"]

		assert KongContract.hostMatchAny(new URI("https://changeme.gelato.io"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://changeme.gelato.io"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("changeme.gelato.io"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://gelato.io"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://gelato.io"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("gelato.io"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://v1.changeme.gelato.io"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://v1.changeme.gelato.io"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("v1.changeme.gelato.io"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://changeme.gelato.io/123/abc?field=id"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://changeme.gelato.io/123/abc?field=id"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("changeme.gelato.io"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://gelato.io/123/abc?field=id"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://gelato.io/123/abc?field=id"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("gelato.io/123/abc?field=id"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://v1.changeme.gelato.io/123/abc?field=id"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://v1.changeme.gelato.io/123/abc?field=id"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("v1.changeme.gelato.io/123/abc?field=id"), trustedHosts)



		assert KongContract.hostMatchAny(new URI("https://changeme.auckland.ac.nz"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://changeme.auckland.ac.nz"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("changeme.auckland.ac.nz"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://auckland.ac.nz"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://auckland.ac.nz"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("auckland.ac.nz"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://v1.changeme.auckland.ac.nz"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://v1.changeme.auckland.ac.nz"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("v1.changeme.auckland.ac.nz"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://changeme.auckland.ac.nz/123/abc?field=id"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://changeme.auckland.ac.nz/123/abc?field=id"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("changeme.auckland.ac.nz"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://auckland.ac.nz/123/abc?field=id"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://auckland.ac.nz/123/abc?field=id"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("auckland.ac.nz/123/abc?field=id"), trustedHosts)

		assert KongContract.hostMatchAny(new URI("https://v1.changeme.auckland.ac.nz/123/abc?field=id"), trustedHosts)
		assert KongContract.hostMatchAny(new URI("http://v1.changeme.auckland.ac.nz/123/abc?field=id"), trustedHosts)
		//assert KongContract.hostMatchAny(new URI("v1.changeme.auckland.ac.nz/123/abc?field=id"), trustedHosts)
	}

	@Test
	public void trustedHostsCheckMustReject() {
		def trustedHosts = ["gelato.io", "auckland.ac.nz"]

		assert !KongContract.hostMatchAny(new URI("http://gelato.io.io"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("http://gelato.io.com"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("https://gelato.io.com"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("https://gelato.io.com/gelato.io"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("https://gelato.io.com/gelato.io?gelato.io"), trustedHosts)
	}

	@Test
	public void trustedHostsCheckMustNotCrash() {
		def trustedHosts = ["gelato.io", "auckland.ac.nz"]

		assert !KongContract.hostMatchAny(new URI("changeme.gelato.io"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("http://localhost"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("http://localhost:8090"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("localhost:8090"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("localhost"), trustedHosts)
		assert !KongContract.hostMatchAny(new URI("http://localhost:8090/abs/123/#home?abs=8"), trustedHosts)

		trustedHosts.add(null)
		assert KongContract.hostMatchAny(new URI("https://gelato.io/123/abc?field=id"), trustedHosts)

		assert !KongContract.hostMatchAny(new URI("https://gelato.io/123/abc?field=id"), [])
		assert !KongContract.hostMatchAny(new URI("https://gelato.io/123/abc?field=id"), null)
	}

	@Test
	public void hostMatchCheckPositive() {
		URI probe = new URI("http://apply.dev.auckland.ac.nz/home/callback")

		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/home/callback"))
		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/home/"))
		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/home"))
		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/"))
		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz"))

		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/roof/callback"))
		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/roof/"))
		assert KongContract.hostMatch(probe, new URI("http://apply.dev.auckland.ac.nz/roof"))

		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz/home/"), probe)
		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz/home"), probe)
		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz/"), probe)
		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz"), probe)

		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz/roof/callback"), probe)
		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz/roof/"), probe)
		assert KongContract.hostMatch(new URI("http://apply.dev.auckland.ac.nz/roof"), probe)
	}

	@Test
	public void hostMatchMustFail() {
		URI probe = new URI("http://apply.dev.auckland.ac.nz/home/callback/callback")

		assert !KongContract.hostMatch(probe, new URI("https://apply.dev.auckland.ac.nz/home/callback"))
		assert !KongContract.hostMatch(probe, new URI("https://apply.dev.auckland.ac.nz/home/"))
		assert !KongContract.hostMatch(probe, new URI("https://apply.dev.auckland.ac.nz/home"))
		assert !KongContract.hostMatch(probe, new URI("https://apply.dev.auckland.ac.nz/"))
		assert !KongContract.hostMatch(probe, new URI("https://apply.dev.auckland.ac.nz"))

		assert !KongContract.hostMatch(new URI("https://apply.dev.auckland.ac.nz/home/"), probe)
		assert !KongContract.hostMatch(new URI("https://apply.dev.auckland.ac.nz/home"), probe)
		assert !KongContract.hostMatch(new URI("https://apply.dev.auckland.ac.nz/"), probe)
		assert !KongContract.hostMatch(new URI("https://apply.dev.auckland.ac.nz"), probe)

		assert !KongContract.hostMatch(probe, new URI("http://dev.auckland.ac.nz/home/callback"))
		assert !KongContract.hostMatch(probe, new URI("http://dev.auckland.ac.nz/home/"))
		assert !KongContract.hostMatch(probe, new URI("http://dev.auckland.ac.nz/home"))
		assert !KongContract.hostMatch(probe, new URI("http://dev.auckland.ac.nz/"))
		assert !KongContract.hostMatch(probe, new URI("http://dev.auckland.ac.nz"))

		assert !KongContract.hostMatch(probe, new URI("http://oops.dev.auckland.ac.nz/home/callback"))
		assert !KongContract.hostMatch(probe, new URI("http://oops.dev.auckland.ac.nz/home/"))
		assert !KongContract.hostMatch(probe, new URI("http://oops.dev.auckland.ac.nz/home"))
		assert !KongContract.hostMatch(probe, new URI("http://oops.dev.auckland.ac.nz/"))
		assert !KongContract.hostMatch(probe, new URI("http://oops.dev.auckland.ac.nz"))

		assert !KongContract.hostMatch(probe, new URI("https://apply.dev.auckland.ac.nz/apply.dev.auckland.ac.nz"))
		assert !KongContract.hostMatch(probe, new URI("http://dev.auckland.ac.nz/apply.dev.auckland.ac.nz"))
		assert !KongContract.hostMatch(probe, new URI("http://oops.dev.auckland.ac.nz/apply.dev.auckland.ac.nz"))
	}

}
