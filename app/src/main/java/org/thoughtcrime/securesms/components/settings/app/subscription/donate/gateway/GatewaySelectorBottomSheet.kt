package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.BadgeDisplay112
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.app.subscription.models.PayPalButton
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.IndeterminateLoadingCircle
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.fragments.requireListener

/**
 * Entry point to capturing the necessary payment token to pay for a donation
 */
class GatewaySelectorBottomSheet : DSLSettingsBottomSheetFragment() {

  private val lifecycleDisposable = LifecycleDisposable()

  private val args: GatewaySelectorBottomSheetArgs by navArgs()

  private val viewModel: GatewaySelectorViewModel by viewModels(factoryProducer = {
    GatewaySelectorViewModel.Factory(args, requireListener<DonationPaymentComponent>().stripeRepository)
  })

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    BadgeDisplay112.register(adapter)
    GooglePayButton.register(adapter)
    PayPalButton.register(adapter)
    IndeterminateLoadingCircle.register(adapter)

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    lifecycleDisposable += viewModel.state.subscribe { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: GatewaySelectorState): DSLConfiguration {
    return configure {
      customPref(
        BadgeDisplay112.Model(
          badge = state.badge,
          withDisplayText = false
        )
      )

      space(12.dp)

      presentTitleAndSubtitle(requireContext(), args.request)

      space(66.dp)

      if (state.loading) {
        customPref(IndeterminateLoadingCircle)
        space(16.dp)
        return@configure
      }

      state.gatewayOrderStrategy.orderedGateways.forEachIndexed { index, gateway ->
        val isFirst = index == 0
        when (gateway) {
          GatewayResponse.Gateway.GOOGLE_PAY -> renderGooglePayButton(state, isFirst)
          GatewayResponse.Gateway.PAYPAL -> renderPayPalButton(state, isFirst)
          GatewayResponse.Gateway.CREDIT_CARD -> renderCreditCardButton(state, isFirst)
          GatewayResponse.Gateway.SEPA_DEBIT -> renderSEPADebitButton(state, isFirst)
        }
      }

      space(16.dp)
    }
  }

  private fun DSLConfiguration.renderGooglePayButton(state: GatewaySelectorState, isFirstButton: Boolean) {
    if (state.isGooglePayAvailable) {
      if (!isFirstButton) {
        space(8.dp)
      }

      customPref(
        GooglePayButton.Model(
          isEnabled = true,
          onClick = {
            findNavController().popBackStack()
            val response = GatewayResponse(GatewayResponse.Gateway.GOOGLE_PAY, args.request)
            setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to response))
          }
        )
      )
    }
  }

  private fun DSLConfiguration.renderPayPalButton(state: GatewaySelectorState, isFirstButton: Boolean) {
    if (state.isPayPalAvailable) {
      if (!isFirstButton) {
        space(8.dp)
      }

      customPref(
        PayPalButton.Model(
          onClick = {
            findNavController().popBackStack()
            val response = GatewayResponse(GatewayResponse.Gateway.PAYPAL, args.request)
            setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to response))
          },
          isEnabled = true
        )
      )
    }
  }

  private fun DSLConfiguration.renderCreditCardButton(state: GatewaySelectorState, isFirstButton: Boolean) {
    if (state.isCreditCardAvailable) {
      if (!isFirstButton) {
        space(8.dp)
      }

      primaryButton(
        text = DSLSettingsText.from(R.string.GatewaySelectorBottomSheet__credit_or_debit_card),
        icon = DSLSettingsIcon.from(R.drawable.credit_card, R.color.signal_colorOnCustom),
        onClick = {
          findNavController().popBackStack()
          val response = GatewayResponse(GatewayResponse.Gateway.CREDIT_CARD, args.request)
          setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to response))
        }
      )
    }
  }

  private fun DSLConfiguration.renderSEPADebitButton(state: GatewaySelectorState, isFirstButton: Boolean) {
    if (state.isSEPADebitAvailable) {
      if (!isFirstButton) {
        space(8.dp)
      }

      tonalButton(
        text = DSLSettingsText.from(R.string.GatewaySelectorBottomSheet__bank_transfer),
        icon = DSLSettingsIcon.from(R.drawable.bank_transfer),
        onClick = {
          findNavController().popBackStack()
          val response = GatewayResponse(GatewayResponse.Gateway.SEPA_DEBIT, args.request)
          setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to response))
        }
      )
    }
  }

  companion object {
    const val REQUEST_KEY = "payment_checkout_mode"

    fun DSLConfiguration.presentTitleAndSubtitle(context: Context, request: GatewayRequest) {
      when (request.donateToSignalType) {
        DonateToSignalType.MONTHLY -> presentMonthlyText(context, request)
        DonateToSignalType.ONE_TIME -> presentOneTimeText(context, request)
        DonateToSignalType.GIFT -> presentGiftText(context, request)
      }
    }

    private fun DSLConfiguration.presentMonthlyText(context: Context, request: GatewayRequest) {
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__donate_s_month_to_signal, FiatMoneyUtil.format(context.resources, request.fiat)),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleLargeModifier
        )
      )
      space(6.dp)
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__get_a_s_badge, request.badge.name),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.BodyLargeModifier,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant))
        )
      )
    }

    private fun DSLConfiguration.presentOneTimeText(context: Context, request: GatewayRequest) {
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, FiatMoneyUtil.format(context.resources, request.fiat)),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleLargeModifier
        )
      )
      space(6.dp)
      noPadTextPref(
        title = DSLSettingsText.from(
          context.resources.getQuantityString(R.plurals.GatewaySelectorBottomSheet__get_a_s_badge_for_d_days, 30, request.badge.name, 30),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.BodyLargeModifier,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant))
        )
      )
    }

    private fun DSLConfiguration.presentGiftText(context: Context, request: GatewayRequest) {
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, FiatMoneyUtil.format(context.resources, request.fiat)),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleLargeModifier
        )
      )
      space(6.dp)
      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.GatewaySelectorBottomSheet__donate_for_a_friend,
          DSLSettingsText.CenterModifier,
          DSLSettingsText.BodyLargeModifier,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(context, R.color.signal_colorOnSurfaceVariant))
        )
      )
    }
  }
}
